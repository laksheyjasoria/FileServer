/**
 * User Management Module – Admin only
 * With token retry, debounced search, and proper visibility toggling.
 */

(function() {
    document.addEventListener('DOMContentLoaded', async function() {

        // ---- Helper: wait for token with retries ----
        function waitForToken(maxRetries = 5, delayMs = 500) {
            return new Promise((resolve) => {
                let attempts = 0;
                const check = () => {
                    const token = localStorage.getItem('jwtToken');
                    if (token) {
                        console.log('✅ Token found after', attempts * delayMs, 'ms');
                        resolve(token);
                        return;
                    }
                    attempts++;
                    if (attempts >= maxRetries) {
                        console.warn('❌ Token not found after', maxRetries * delayMs, 'ms – redirecting');
                        resolve(null);
                        return;
                    }
                    setTimeout(check, delayMs);
                };
                check();
            });
        }

        // ---- Debounce helper ----
        function debounce(func, wait) {
            let timeout;
            return function(...args) {
                clearTimeout(timeout);
                timeout = setTimeout(() => func.apply(this, args), wait);
            };
        }

        // ---- DOM refs ----
        const mainApp = document.getElementById('mainApp');
        const userContainer = document.getElementById('userContainer');
        const loadingIndicator = document.getElementById('loadingIndicator');
        const userTable = document.getElementById('userTable');
        const userTableBody = document.getElementById('userTableBody');
        const paginationDiv = document.getElementById('pagination');
        const pageInfo = document.getElementById('pageInfo');
        const prevBtn = document.getElementById('prevPage');
        const nextBtn = document.getElementById('nextPage');
        const searchInput = document.getElementById('searchInput');
        const statusFilter = document.getElementById('statusFilter');
        const searchBtn = document.getElementById('searchBtn');
        const resetBtn = document.getElementById('resetBtn');

        // ---- Debug ----
        console.log('DOM elements found:', {
            mainApp: !!mainApp,
            userContainer: !!userContainer,
            loadingIndicator: !!loadingIndicator,
            userTable: !!userTable,
            userTableBody: !!userTableBody,
            paginationDiv: !!paginationDiv
        });

        // ---- Wait for token ----
        const token = await waitForToken(5, 500);
        if (!token) {
            window.location.href = '/login.html';
            return;
        }

        // ---- Validate token ----
        if (!isTokenValid()) {
            console.warn('Token is invalid or expired – redirecting to login');
            localStorage.removeItem('jwtToken');
            window.location.href = '/login.html';
            return;
        }

        // ---- Check admin role ----
        const user = getUserFromToken();
        console.log('User from token:', user);
        if (!user || user.role !== 'ADMIN') {
            console.warn('User is not ADMIN – redirecting to index');
            window.location.href = '/index.html?error=unauthorized';
            return;
        }

        // ---- Show main app ----
        if (mainApp) mainApp.style.display = 'block';
        loadUserInfoIntoUI();

        // ---- State ----
        let currentPage = 0;
        let totalPages = 0;
        let totalElements = 0;
        let isLoading = false;

        // ---- Load users ----
        await fetchUsers(0);

        // ---- Event listeners ----
        if (searchBtn) {
            searchBtn.addEventListener('click', () => fetchUsers(0));
        }
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                if (statusFilter) statusFilter.value = '';
                if (searchInput) searchInput.value = '';
                fetchUsers(0);
            });
        }
        if (searchInput) {
            // Debounced search on input
            const debouncedSearch = debounce(() => fetchUsers(0), 300);
            searchInput.addEventListener('input', debouncedSearch);
            // Enter key also triggers search
            searchInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') fetchUsers(0); });
        }
        if (prevBtn) {
            prevBtn.addEventListener('click', () => { if (currentPage > 0) fetchUsers(currentPage - 1); });
        }
        if (nextBtn) {
            nextBtn.addEventListener('click', () => { if (currentPage < totalPages - 1) fetchUsers(currentPage + 1); });
        }

        // ---- Table action delegation ----
        if (userTableBody) {
            userTableBody.addEventListener('click', async (e) => {
                const btn = e.target.closest('button');
                if (!btn) return;
                const id = btn.dataset.id;
                const action = btn.dataset.action;
                if (!id || !action) return;

                if (action === 'delete') {
                    const confirmed = await showConfirm(
                        'Permanently delete this user? This cannot be undone.',
                        'Delete User',
                        'Delete',
                        'Cancel',
                        'danger'
                    );
                    if (!confirmed) return;
                    await performAction(id, 'delete');
                } else if (action === 'activate' || action === 'deactivate') {
                    const newStatus = action === 'activate' ? 'ACTIVE' : 'DEACTIVATED';
                    const confirmed = await showConfirm(
                        `Set user status to ${newStatus}?`,
                        'Update Status',
                        'Confirm',
                        'Cancel'
                    );
                    if (!confirmed) return;
                    await performAction(id, 'status', newStatus);
                }
            });
        }

        // ---- Action helper ----
        async function performAction(id, type, payload) {
            try {
                let url, method, body;
                if (type === 'delete') {
                    url = `/api/admin/users/${id}`;
                    method = 'DELETE';
                } else if (type === 'status') {
                    url = `/api/admin/users/${id}/status`;
                    method = 'PUT';
                    body = JSON.stringify({ status: payload });
                }
                const response = await apiCall(url, {
                    method,
                    headers: { 'Content-Type': 'application/json' },
                    body
                });
                if (!response || !response.ok) {
                    const text = response ? await response.text() : 'No response';
                    throw new Error(text || `HTTP ${response ? response.status : 'unknown'}`);
                }
                showToast('Action successful', 'success');
                fetchUsers(currentPage);
            } catch (err) {
                showToast('Error: ' + err.message, 'error');
            }
        }

        // ---- Fetch users ----
        async function fetchUsers(page = 0) {
            if (isLoading) return;
            isLoading = true;

            // Show loading, hide table and pagination
            if (loadingIndicator) loadingIndicator.style.display = 'block';
            if (userTable) userTable.style.display = 'none';
            if (paginationDiv) paginationDiv.style.display = 'none';

            const status = statusFilter ? statusFilter.value : '';
            const search = searchInput ? searchInput.value.trim() : '';
            const params = new URLSearchParams();
            params.set('page', page);
            params.set('size', 20);
            if (status) params.set('status', status);
            if (search) params.set('search', search);

            const endpoint = `/api/admin/users?${params.toString()}`;

            try {
                const response = await apiCall(endpoint, { skipDedupe: true });
                if (!response) {
                    isLoading = false;
                    return;
                }
                if (!response.ok) {
                    if (response.status === 401 || response.status === 403) {
                        showToast('Unauthorized – please log in again', 'error');
                        localStorage.removeItem('jwtToken');
                        setTimeout(() => window.location.href = '/login.html', 1500);
                        return;
                    }
                    throw new Error(`HTTP ${response.status}`);
                }
                const data = await response.json();
                console.log('✅ User data received:', data);

                if (!userTableBody) {
                    console.error('❌ userTableBody element not found!');
                    if (loadingIndicator) loadingIndicator.style.display = 'none';
                    if (userContainer) {
                        const errorDiv = document.createElement('div');
                        errorDiv.className = 'empty-state';
                        errorDiv.textContent = 'Error: Table body not found. Please refresh.';
                        userContainer.appendChild(errorDiv);
                    }
                    return;
                }

                renderUsers(data.content);
                currentPage = data.number;
                totalPages = data.totalPages;
                totalElements = data.totalElements;
                updatePagination();

                // Hide loading, show table and pagination
                if (loadingIndicator) loadingIndicator.style.display = 'none';
                if (userTable) userTable.style.display = 'table';
                if (paginationDiv) paginationDiv.style.display = 'flex';
            } catch (err) {
                console.error(err);
                showToast('Failed to load users: ' + err.message, 'error');
                if (loadingIndicator) loadingIndicator.style.display = 'none';
                if (userContainer) {
                    const errorDiv = document.createElement('div');
                    errorDiv.className = 'empty-state';
                    errorDiv.textContent = 'Error loading users';
                    userContainer.appendChild(errorDiv);
                }
            } finally {
                isLoading = false;
            }
        }

        // ---- Render ----
        function renderUsers(users) {
            console.log('Rendering users:', users);
            if (!userTableBody) {
                console.error('❌ userTableBody is null in renderUsers');
                return;
            }
            if (!users || users.length === 0) {
                userTableBody.innerHTML = `<tr><td colspan="5" style="text-align:center; color:#9ca3af; padding:2rem;">No users found</td></tr>`;
                return;
            }
            let html = '';
            users.forEach(u => {
                const statusClass = 'status-' + u.status.toLowerCase();
                const actions = `
                    <button class="btn-sm ${u.status === 'ACTIVE' ? 'btn-deactivate' : 'btn-activate'}"
                            data-id="${u.id}" data-action="${u.status === 'ACTIVE' ? 'deactivate' : 'activate'}">
                        ${u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                    </button>
                    <button class="btn-sm btn-delete" data-id="${u.id}" data-action="delete">Delete</button>
                `;
                html += `
                    <tr>
                        <td>${escapeHtml(u.email)}</td>
                        <td>${escapeHtml(u.name || '-')}</td>
                        <td><span class="status-badge ${statusClass}">${u.status}</span></td>
                        <td>${u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '-'}</td>
                        <td>${actions}</td>
                    </tr>
                `;
            });
            userTableBody.innerHTML = html;
            console.log('✅ Users rendered successfully');
        }

        // ---- Pagination ----
        function updatePagination() {
            if (pageInfo) {
                pageInfo.textContent = `Page ${currentPage + 1} of ${totalPages || 1} (${totalElements} users)`;
            }
            if (prevBtn) {
                prevBtn.disabled = currentPage === 0;
            }
            if (nextBtn) {
                nextBtn.disabled = currentPage >= totalPages - 1;
            }
        }
    });
})();