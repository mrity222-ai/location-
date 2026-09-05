const token = localStorage.getItem('admin_token');
if (!token) {
    window.location.href = '/admin';
}

document.getElementById('logoutBtn').addEventListener('click', () => {
    localStorage.removeItem('admin_token');
    window.location.href = '/admin';
});

async function loadSettings() {
    try {
        const res = await fetch('/api/admin/settings', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.status === 401 || res.status === 403) return logout();
        const data = await res.json();

        document.getElementById('smtp_host').value = data.smtp_host || 'smtp.gmail.com';
        document.getElementById('smtp_port').value = data.smtp_port || '587';
        document.getElementById('smtp_user').value = data.smtp_user || '';
        document.getElementById('smtp_pass').value = data.smtp_pass || '';
        document.getElementById('smtp_from').value = data.smtp_from || 'LocationSpoofer Admin <no-reply@locationsofi.com>';
        document.getElementById('jwt_secret').value = data.jwt_secret || 'locationsofi_super_secret_jwt_key_2026';
        document.getElementById('default_device_status').value = data.default_device_status || 'PENDING';
        document.getElementById('default_expiry_days').value = data.default_expiry_days || '30';
    } catch (e) {
        console.error('Failed to load settings:', e);
    }
}

document.getElementById('saveAllBtn').addEventListener('click', async (e) => {
    e.preventDefault();
    const alertBox = document.getElementById('alertBox');
    alertBox.classList.add('d-none');

    const payload = {
        smtp_host: document.getElementById('smtp_host').value,
        smtp_port: document.getElementById('smtp_port').value,
        smtp_user: document.getElementById('smtp_user').value,
        smtp_pass: document.getElementById('smtp_pass').value,
        smtp_from: document.getElementById('smtp_from').value,
        jwt_secret: document.getElementById('jwt_secret').value,
        default_device_status: document.getElementById('default_device_status').value,
        default_expiry_days: document.getElementById('default_expiry_days').value,
        current_password: document.getElementById('current_password').value,
        new_password: document.getElementById('new_password').value
    };

    try {
        const res = await fetch('/api/admin/settings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify(payload)
        });
        const data = await res.json();

        alertBox.classList.remove('d-none', 'alert-danger', 'alert-success');
        if (res.ok) {
            alertBox.classList.add('alert-success');
            alertBox.innerText = '✅ Settings saved successfully!';
            document.getElementById('current_password').value = '';
            document.getElementById('new_password').value = '';
        } else {
            alertBox.classList.add('alert-danger');
            alertBox.innerText = data.error || 'Failed to save settings.';
        }
    } catch (err) {
        alertBox.classList.remove('d-none');
        alertBox.classList.add('alert-danger');
        alertBox.innerText = 'Connection error while saving settings.';
    }
});

document.getElementById('testSmtpBtn').addEventListener('click', async () => {
    const btn = document.getElementById('testSmtpBtn');
    const alertBox = document.getElementById('alertBox');
    btn.disabled = true;
    btn.innerHTML = '<i class="bi bi-hourglass-split"></i> Sending Test Mail...';
    alertBox.classList.add('d-none');

    try {
        const res = await fetch('/api/admin/test-smtp', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ target_email: document.getElementById('smtp_user').value })
        });
        const data = await res.json();
        alertBox.classList.remove('d-none', 'alert-danger', 'alert-success');
        if (res.ok) {
            alertBox.classList.add('alert-success');
            alertBox.innerText = `✅ ${data.message}`;
        } else {
            alertBox.classList.add('alert-danger');
            alertBox.innerText = `❌ ${data.error}`;
        }
    } catch (e) {
        alertBox.classList.remove('d-none');
        alertBox.classList.add('alert-danger');
        alertBox.innerText = '❌ Failed to connect to server for SMTP test.';
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="bi bi-send-fill me-1"></i> Test SMTP Email';
    }
});

function logout() {
    localStorage.removeItem('admin_token');
    window.location.href = '/admin';
}

loadSettings();
