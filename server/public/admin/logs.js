const token = localStorage.getItem('admin_token');
if (!token) {
    window.location.href = '/admin';
}

document.getElementById('logoutBtn').addEventListener('click', () => {
    localStorage.removeItem('admin_token');
    window.location.href = '/admin';
});

document.getElementById('refreshBtn').addEventListener('click', loadLogs);

let allLogs = [];

async function loadLogs() {
    try {
        const res = await fetch('/api/admin/logs', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.status === 401 || res.status === 403) return logout();
        allLogs = await res.json();
        renderLogs();
    } catch (e) {
        console.error(e);
    }
}

function renderLogs() {
    const tbody = document.getElementById('logsTableBody');
    if (!tbody) return;

    const searchTerm = (document.getElementById('logSearchInput').value || '').toLowerCase();

    let filtered = allLogs.filter(log => {
        if (!searchTerm) return true;
        return (log.action || '').toLowerCase().includes(searchTerm) ||
               (log.details || '').toLowerCase().includes(searchTerm) ||
               (log.performed_by || '').toLowerCase().includes(searchTerm) ||
               (log.ip_address || '').toLowerCase().includes(searchTerm);
    });

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-muted">No activity logs found.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map((log, index) => {
        const time = log.created_at ? new Date(log.created_at).toLocaleString() : 'N/A';
        let actionBadge = 'bg-secondary';
        if (log.action.includes('APPROVED') || log.action.includes('SUCCESS')) actionBadge = 'bg-success';
        else if (log.action.includes('BLOCKED') || log.action.includes('FAILED')) actionBadge = 'bg-danger';
        else if (log.action.includes('REGISTERED') || log.action.includes('SET')) actionBadge = 'bg-primary';
        else if (log.action.includes('UPDATED') || log.action.includes('RESET')) actionBadge = 'bg-warning text-dark';

        return `
            <tr>
                <td>${index + 1}</td>
                <td><small class="text-secondary">${time}</small></td>
                <td><span class="badge ${actionBadge}">${escapeHtml(log.action)}</span></td>
                <td><small class="fw-semibold text-dark">${escapeHtml(log.details)}</small></td>
                <td><code class="text-primary">${escapeHtml(log.performed_by || 'System')}</code></td>
                <td><small class="text-muted">${escapeHtml(log.ip_address || '127.0.0.1')}</small></td>
            </tr>
        `;
    }).join('');
}

document.getElementById('logSearchInput').addEventListener('input', renderLogs);

function logout() {
    localStorage.removeItem('admin_token');
    window.location.href = '/admin';
}

function escapeHtml(str) {
    return String(str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

loadLogs();
