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

document.getElementById('refreshBtn').addEventListener('click', loadDevices);

let allDevices = [];
let currentFilter = 'ALL';

async function loadDevices() {
    try {
        const res = await fetch('/api/admin/devices', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.status === 401 || res.status === 403) return logout();
        allDevices = await res.json();
        renderDevices();
    } catch (e) {
        console.error(e);
    }
}

function renderDevices() {
    const tbody = document.getElementById('deviceTableBody');
    const searchTerm = (document.getElementById('searchInput').value || '').toLowerCase();

    let filtered = allDevices.filter(d => {
        const matchesStatus = currentFilter === 'ALL' || d.status === currentFilter;
        const matchesSearch = !searchTerm || 
            (d.hwid || '').toLowerCase().includes(searchTerm) || 
            (d.user_tag || '').toLowerCase().includes(searchTerm) ||
            (d.registered_name || '').toLowerCase().includes(searchTerm) ||
            (d.user_email || '').toLowerCase().includes(searchTerm);
        return matchesStatus && matchesSearch;
    });

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center py-4 text-muted">No registered users found.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map((d, index) => {
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
}

let activeUserOffcanvas = null;

function openUserBiodataPanel(id) {
    const d = allDevices.find(dev => dev.id === id);
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

    if (!activeUserOffcanvas) {
        activeUserOffcanvas = bootstrap.Offcanvas.getOrCreateInstance(document.getElementById('userProfileOffcanvas'));
    }
    activeUserOffcanvas.show();
}

function hideOffcanvas() {
    if (activeUserOffcanvas) {
        activeUserOffcanvas.hide();
    }
}

function offcanvasActionApprove(id) {
    hideOffcanvas();
    quickUpdateStatus(id, 'APPROVED');
}

function offcanvasActionBlock(id) {
    hideOffcanvas();
    quickUpdateStatus(id, 'BLOCKED');
}

function offcanvasActionEdit(id, tag, status, expiry) {
    hideOffcanvas();
    openEditModal(id, tag, status, expiry);
}

function offcanvasActionLocation(id, lat, lng) {
    hideOffcanvas();
    openLocationModal(id, lat, lng);
}

document.getElementById('searchInput').addEventListener('input', renderDevices);

function filterStatus(status, btnElement) {
    currentFilter = status;
    document.querySelectorAll('.btn-group .btn').forEach(b => b.classList.remove('active'));
    if (btnElement) btnElement.classList.add('active');
    renderDevices();
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
    loadDevices();
});

async function quickUpdateStatus(id, status) {
    await fetch('/api/admin/device/update-status', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ id, status })
    });
    loadDevices();
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
    loadDevices();
});

async function clearRemoteLocation() {
    const id = document.getElementById('locDeviceId').value;
    await fetch('/api/admin/device/set-location', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ id, remote_lat: null, remote_lng: null })
    });

    locationModal.hide();
    loadDevices();
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

// Auto filter if URL parameter status exists
const urlParams = new URLSearchParams(window.location.search);
const urlStatus = urlParams.get('status');
if (urlStatus) {
    currentFilter = urlStatus;
}

loadDevices();
