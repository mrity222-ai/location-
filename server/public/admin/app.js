const token = localStorage.getItem('admin_token');
if (!token) {
    window.location.href = '/admin';
}

const editModal = new bootstrap.Modal(document.getElementById('editModal'));
const locationModal = new bootstrap.Modal(document.getElementById('locationModal'));

document.getElementById('logoutBtn').addEventListener('click', () => {
    localStorage.removeItem('admin_token');
    window.location.href = '/admin';
});

document.getElementById('refreshBtn').addEventListener('click', loadData);

let lineChartInstance = null;
let donutChartInstance = null;

async function loadData() {
    await Promise.all([loadStats(), loadDevices(), loadLogs()]);
}

function initCharts(stats) {
    const total = stats.total || 0;
    const approved = stats.APPROVED || 0;
    const pending = stats.PENDING || 0;
    const blocked = stats.BLOCKED || 0;
    const expired = stats.EXPIRED || 0;

    // 1. Activity Line Chart Setup
    const ctxLine = document.getElementById('activityLineChart');
    if (ctxLine) {
        if (lineChartInstance) lineChartInstance.destroy();
        const gradient = ctxLine.getContext('2d').createLinearGradient(0, 0, 0, 200);
        gradient.addColorStop(0, 'rgba(59, 130, 246, 0.25)');
        gradient.addColorStop(1, 'rgba(59, 130, 246, 0.0)');

        lineChartInstance = new Chart(ctxLine, {
            type: 'line',
            data: {
                labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
                datasets: [{
                    label: 'Device Activity',
                    data: [1.5, 3.0, 2.0, 3.2, 4.0, 3.0, 5.1, 5.5, 3.2, 4.7, 4.0, 6.0],
                    borderColor: '#3b82f6',
                    borderWidth: 3,
                    fill: true,
                    backgroundColor: gradient,
                    tension: 0.4,
                    pointBackgroundColor: '#ffffff',
                    pointBorderColor: '#3b82f6',
                    pointBorderWidth: 2,
                    pointRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { display: false }, ticks: { font: { size: 10, weight: '600' }, color: '#94a3b8' } },
                    y: { grid: { borderDash: [4, 4], color: '#f1f5f9' }, ticks: { font: { size: 10, weight: '600' }, color: '#94a3b8' } }
                }
            }
        });
    }

    // 2. Status Donut Chart Setup
    const ctxDonut = document.getElementById('statusDonutChart');
    if (ctxDonut) {
        if (donutChartInstance) donutChartInstance.destroy();
        donutChartInstance = new Chart(ctxDonut, {
            type: 'doughnut',
            data: {
                labels: ['Approved', 'Pending Approval', 'Blocked', 'Expired'],
                datasets: [{
                    data: [approved, pending, blocked, expired],
                    backgroundColor: ['#10b981', '#f59e0b', '#ef4444', '#94a3b8'],
                    borderWidth: 0,
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '76%',
                plugins: { legend: { display: false }, tooltip: { enabled: true } }
            }
        });
    }
}

async function loadStats() {
    try {
        const res = await fetch('/api/admin/stats', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.status === 401 || res.status === 403) return logout();
        const data = await res.json();

        const total = data.total || 0;
        const approved = data.APPROVED || 0;
        const pending = data.PENDING || 0;
        const blocked = data.BLOCKED || 0;
        const expired = data.EXPIRED || 0;

        document.getElementById('statTotal').innerText = total;
        document.getElementById('statApproved').innerText = approved;
        document.getElementById('statPending').innerText = pending;
        document.getElementById('statBlocked').innerText = blocked;
        document.getElementById('statExpired').innerText = expired;

        // Sidebar & Legend Updates
        const sidebarPending = document.getElementById('sidebarPendingBadge');
        if (sidebarPending) sidebarPending.innerText = `+${pending}`;

        const donutTotal = document.getElementById('donutTotalText');
        if (donutTotal) donutTotal.innerText = total;

        const legApproved = document.getElementById('legendApproved');
        if (legApproved) legApproved.innerText = approved;

        const legPending = document.getElementById('legendPending');
        if (legPending) legPending.innerText = pending;

        const legBlocked = document.getElementById('legendBlocked');
        if (legBlocked) legBlocked.innerText = blocked;

        const legExpired = document.getElementById('legendExpired');
        if (legExpired) legExpired.innerText = expired;

        initCharts(data);
    } catch (e) {
        console.error(e);
    }
}

async function loadDevices() {
    try {
        const res = await fetch('/api/admin/devices', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.status === 401 || res.status === 403) return logout();
        const devices = await res.json();
        allDashboardDevices = devices;
        const top10Devices = devices.slice(0, 10);

        const tbody = document.getElementById('deviceTableBody');
        if (top10Devices.length === 0) {
            tbody.innerHTML = `<tr><td colspan="8" class="text-center py-4 text-muted">No devices registered yet.</td></tr>`;
            return;
        }

        tbody.innerHTML = top10Devices.map((d, index) => {
            const expiry = d.expiry_date ? new Date(d.expiry_date).toLocaleString() : 'Lifetime';
            const location = (d.remote_lat && d.remote_lng) ? `${d.remote_lat.toFixed(4)}, ${d.remote_lng.toFixed(4)}` : '<span class="text-muted">Default</span>';
            const lastSeen = d.last_seen ? new Date(d.last_seen).toLocaleTimeString() : 'N/A';
            const displayName = d.registered_name || d.user_tag || 'Registered User';
            const displayEmail = d.user_email ? `<br><small class="text-primary fw-semibold">✉️ ${escapeHtml(d.user_email)}</small>` : '';

            const approveBtn = `<button class="btn-act btn-act-approve me-1" onclick="quickUpdateStatus(${d.id}, 'APPROVED')">🟢 Approve</button>`;
            const blockBtn = d.status === 'BLOCKED' 
                ? `<button class="btn-act btn-act-approve me-1" onclick="quickUpdateStatus(${d.id}, 'APPROVED')">🟢 Unblock</button>`
                : `<button class="btn-act btn-act-block me-1" onclick="quickUpdateStatus(${d.id}, 'BLOCKED')">🔴 Block</button>`;

            return `
                <tr>
                    <td>${index + 1}</td>
                    <td>
                        <a href="javascript:void(0)" onclick="openUserBiodataPanel(${d.id})" class="text-decoration-none">
                            <div class="fw-bold text-primary">${escapeHtml(displayName)} ↗</div>
                            ${displayEmail}
                        </a>
                    </td>
                    <td><code class="hwid-code">${escapeHtml(d.hwid)}</code></td>
                    <td><span class="badge-pill badge-${d.status}">${d.status}</span></td>
                    <td><small class="text-secondary">${expiry}</small></td>
                    <td><small class="text-secondary">${location}</small></td>
                    <td><small class="text-muted">${lastSeen}</small></td>
                    <td>
                        ${approveBtn}
                        <button class="btn-act btn-act-edit me-1" onclick="openEditModal(${d.id}, '${escapeJs(displayName)}', '${d.status}', '${d.expiry_date || ''}')">✏️ Edit</button>
                        ${blockBtn}
                        <button class="btn-act btn-act-loc" onclick="openLocationModal(${d.id}, ${d.remote_lat || 'null'}, ${d.remote_lng || 'null'})">🛰️ Location</button>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (e) {
        console.error(e);
    }
}

let allDashboardDevices = [];
let activeDashboardOffcanvas = null;

function openUserBiodataPanel(id) {
    const d = allDashboardDevices.find(dev => dev.id === id);
    if (!d) return;

    const displayName = d.registered_name || d.user_tag || 'Registered User';
    const displayEmail = d.user_email || 'No Email Registered';

    document.getElementById('offcanvasUserName').innerText = displayName;
    document.getElementById('offcanvasUserEmail').innerText = displayEmail;
    document.getElementById('offcanvasUserAvatar').innerText = displayName.charAt(0).toUpperCase();

    const badgeEl = document.getElementById('offcanvasStatusBadge');
    badgeEl.className = `badge-pill badge-${d.status}`;
    badgeEl.innerText = d.status;

    document.getElementById('offcanvasHwid').innerText = d.hwid || 'N/A';
    document.getElementById('offcanvasCreatedAt').innerText = d.created_at ? new Date(d.created_at).toLocaleDateString() : 'N/A';
    document.getElementById('offcanvasLastSeen').innerText = d.last_seen ? new Date(d.last_seen).toLocaleString() : 'N/A';
    document.getElementById('offcanvasExpiry').innerText = d.expiry_date ? new Date(d.expiry_date).toLocaleString() : 'Lifetime Access';
    
    const locText = (d.remote_lat && d.remote_lng) ? `Lat: ${d.remote_lat.toFixed(5)}, Lng: ${d.remote_lng.toFixed(5)}` : 'Default Hardware GPS';
    document.getElementById('offcanvasLocation').innerText = locText;

    const actionContainer = document.getElementById('offcanvasActionButtons');
    actionContainer.innerHTML = `
        <button class="btn btn-success fw-bold rounded-3 py-2 mb-1" onclick="offcanvasActionApprove(${d.id})">🟢 Approve License Access</button>
        <button class="btn btn-outline-primary fw-bold rounded-3 py-2 mb-1" onclick="offcanvasActionEdit(${d.id}, '${escapeJs(displayName)}', '${d.status}', '${d.expiry_date || ''}')">✏️ Edit Details & Expiry</button>
        <button class="btn btn-outline-info fw-bold rounded-3 py-2 mb-1" onclick="offcanvasActionLocation(${d.id}, ${d.remote_lat || 'null'}, ${d.remote_lng || 'null'})">🛰️ Set Remote Mock Location</button>
        ${d.status === 'BLOCKED' 
            ? `<button class="btn btn-success fw-bold rounded-3 py-2" onclick="offcanvasActionApprove(${d.id})">🟢 Unblock User</button>`
            : `<button class="btn btn-danger fw-bold rounded-3 py-2" onclick="offcanvasActionBlock(${d.id})">🔴 Block User Access</button>`
        }
    `;

    if (!activeDashboardOffcanvas) {
        activeDashboardOffcanvas = bootstrap.Offcanvas.getOrCreateInstance(document.getElementById('userProfileOffcanvas'));
    }
    activeDashboardOffcanvas.show();
}

function hideDashboardOffcanvas() {
    if (activeDashboardOffcanvas) {
        activeDashboardOffcanvas.hide();
    }
}

function offcanvasActionApprove(id) {
    hideDashboardOffcanvas();
    quickUpdateStatus(id, 'APPROVED');
}

function offcanvasActionBlock(id) {
    hideDashboardOffcanvas();
    quickUpdateStatus(id, 'BLOCKED');
}

function offcanvasActionEdit(id, tag, status, expiry) {
    hideDashboardOffcanvas();
    openEditModal(id, tag, status, expiry);
}

function offcanvasActionLocation(id, lat, lng) {
    hideDashboardOffcanvas();
    openLocationModal(id, lat, lng);
}

async function loadLogs() {
    try {
        const res = await fetch('/api/admin/logs', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.status === 401 || res.status === 403) return logout();
        const logs = await res.json();
        const top10Logs = logs.slice(0, 10);

        const tbody = document.getElementById('logsTableBody');
        if (!tbody) return;

        if (top10Logs.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-muted">No activity logs recorded yet.</td></tr>`;
            return;
        }

        tbody.innerHTML = top10Logs.map((log, index) => {
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
                    <td><small>${escapeHtml(log.details)}</small></td>
                    <td><code class="text-primary">${escapeHtml(log.performed_by || 'System')}</code></td>
                    <td><small class="text-muted">${escapeHtml(log.ip_address || '127.0.0.1')}</small></td>
                </tr>
            `;
        }).join('');
    } catch (e) {
        console.error(e);
    }
}

function openEditModal(id, tag, status, expiry) {
    document.getElementById('editDeviceId').value = id;
    document.getElementById('editUserTag').value = tag;
    document.getElementById('editStatus').value = status;
    document.getElementById('editExpiryDate').value = expiry ? new Date(expiry).toISOString().slice(0, 16) : '';
    editModal.show();
}

function setPresetExpiry(days) {
    if (!days) {
        document.getElementById('editExpiryDate').value = '';
        return;
    }
    const d = new Date();
    d.setDate(d.getDate() + days);
    document.getElementById('editExpiryDate').value = d.toISOString().slice(0, 16);
}

document.getElementById('saveEditBtn').addEventListener('click', async () => {
    const id = document.getElementById('editDeviceId').value;
    const user_tag = document.getElementById('editUserTag').value;
    const status = document.getElementById('editStatus').value;
    const expiry_date = document.getElementById('editExpiryDate').value;

    await fetch('/api/admin/device/update-status', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ id, user_tag, status, expiry_date })
    });

    editModal.hide();
    loadData();
});

async function quickUpdateStatus(id, status) {
    await fetch('/api/admin/device/update-status', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ id, status })
    });
    loadData();
}

function openLocationModal(id, lat, lng) {
    document.getElementById('locDeviceId').value = id;
    document.getElementById('locLat').value = lat || '';
    document.getElementById('locLng').value = lng || '';
    locationModal.show();
}

document.getElementById('saveLocationBtn').addEventListener('click', async () => {
    const id = document.getElementById('locDeviceId').value;
    const remote_lat = parseFloat(document.getElementById('locLat').value);
    const remote_lng = parseFloat(document.getElementById('locLng').value);

    await fetch('/api/admin/device/set-location', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ id, remote_lat, remote_lng })
    });

    locationModal.hide();
    loadData();
});

async function clearRemoteLocation() {
    const id = document.getElementById('locDeviceId').value;
    await fetch('/api/admin/device/set-location', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ id, remote_lat: null, remote_lng: null })
    });

    locationModal.hide();
    loadData();
}

function filterDeviceTable(status) {
    const rows = document.querySelectorAll('#deviceTableBody tr');
    rows.forEach(r => {
        if (!status) {
            r.style.display = '';
        } else {
            const text = r.innerText || '';
            r.style.display = text.includes(status) ? '' : 'none';
        }
    });
}

function logout() {
    localStorage.removeItem('admin_token');
    window.location.href = '/admin';
}

function escapeHtml(str) {
    return String(str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
function escapeJs(str) {
    return String(str || '').replace(/'/g, "\\'");
}

loadData();