// googleAuth.js – Google Sign-In (renders GSI button)

(function() {
    let initialized = false;

    function initGoogleSignIn() {
        if (initialized) return;
        if (typeof google === 'undefined' || typeof google.accounts === 'undefined') {
            console.warn('Google SDK not loaded yet. Retrying...');
            setTimeout(initGoogleSignIn, 500);
            return;
        }

        fetch(`${API_URL}/auth/google/client-id`)
            .then(response => {
                if (!response.ok) throw new Error('Failed to fetch client ID');
                return response.text();
            })
            .then(clientId => {
                if (!clientId || clientId.trim() === '') {
                    throw new Error('Client ID is empty');
                }
                initialized = true;

                google.accounts.id.initialize({
                    client_id: clientId.trim(),
                    callback: handleCredentialResponse,
                    cancel_on_tap_outside: false,
                    use_fedcm: false,
                    context: 'signin'
                });

                // Render the GSI button in the container
                const container = document.querySelector('.g_id_signin');
                if (container) {
                    google.accounts.id.renderButton(container, {
                        type: 'standard',
                        theme: 'outline',
                        size: 'large',
                        text: 'continue_with',
                        shape: 'pill',
                        width: '315',
                        logo_alignment: 'center'
                    });
                    console.log('✅ Google GSI button rendered.');
                } else {
                    console.warn('Container .g_id_signin not found.');
                }
            })
            .catch(error => {
                console.error('Google Sign-In setup failed:', error);
                if (typeof showToast === 'function') {
                    showToast('Google Sign-In is not available. Please use email/password.', 'warning');
                }
            });
    }

    window.handleCredentialResponse = function(response) {
        const idToken = response.credential;
        const isSync = sessionStorage.getItem('googleSyncMode') === 'true' || window._syncMode === true;
        sessionStorage.removeItem('googleSyncMode');
        window._syncMode = false;

        fetch(`${API_URL}/auth/google?sync=${isSync}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ idToken })
        })
            .then(r => r.json())
            .then(json => {
                if (json && json.success) {
                    localStorage.setItem('jwtToken', json.data);
                    if (isSync) {
                        if (typeof showToast === 'function') {
                            showToast('Profile synced with Google! Redirecting...', 'success', 2000);
                        }
                        setTimeout(() => window.location.href = '/profile.html', 1500);
                    } else {
                        if (typeof showToast === 'function') {
                            showToast('Google Sign-In successful. Redirecting...', 'success', 2000);
                        }
                        setTimeout(() => window.location.href = '/index.html', 1500);
                    }
                } else {
                    const msg = json.message || 'Google sign-in failed';
                    if (typeof showToast === 'function') showToast(msg, 'error');
                    else alert(msg);
                }
            })
            .catch(err => {
                console.error('Google sign-in error:', err);
                if (typeof showToast === 'function') showToast('Network error during Google sign-in.', 'error');
                else alert('Network error');
            });
    };

    // ===== Trigger Google sync (profile page) =====
    window.triggerGoogleSync = function() {
        if (!initialized) {
            if (typeof showToast === 'function') {
                showToast('Google Sign-In is still loading. Please wait a moment and try again.', 'info');
            }
            return;
        }
        sessionStorage.setItem('googleSyncMode', 'true');
        window.location.href = '/login.html?sync=true';
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initGoogleSignIn);
    } else {
        initGoogleSignIn();
    }
})();