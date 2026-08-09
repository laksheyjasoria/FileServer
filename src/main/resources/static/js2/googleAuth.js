// googleAuth.js - Common logic for Google Sign-In

// Initialize the Google button when the page loads
window.onload = async function() {
    try {
        // 1. Fetch the Client ID dynamically from your Spring Boot backend
        const response = await fetch(`${API_URL}/auth/google/client-id`);
        if (!response.ok) {
            throw new Error('Failed to fetch Google Client ID');
        }
        const clientId = await response.text();

        // 2. Initialize the Google Sign-In logic
        if (window.google && clientId) {
            google.accounts.id.initialize({ 
                client_id: clientId, 
                callback: handleCredentialResponse 
            });
            
            // 3. Render the standard Google button
            google.accounts.id.renderButton(
                document.querySelector('.g_id_signin'), 
                { theme: 'outline', size: 'large' }
            );
        }
    } catch (error) {
        console.error('Google Sign-In setup failed:', error);
        // Hide the button if something went wrong
        const button = document.querySelector('.g_id_signin');
        if (button) button.style.display = 'none';
    }
};

// 4. Handle the credential response from Google
function handleCredentialResponse(response) {
    const idToken = response.credential;
    fetch(`${API_URL}/auth/google`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken })
    })
    .then(r => r.json())
    .then(json => {
        if (json && json.success) {
            localStorage.setItem('jwtToken', json.data);
            window.location.href = '/index.html';
        } else {
            showError(json.message || 'Google sign-in failed');
        }
    })
    .catch(err => showError('Network error'));
}