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
        const matchesSearch = !searchTerm || (d.hwid || '').toLowerCase().includes(searchTerm) || (d.user_tag || '').toLowerCase().includes(searchTerm);
        return matchesStatus && matchesSearch;
    });

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center py-4 text-muted">No devices found.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map((d, index) => {
        const expiry = d.expiry_date ? new Date(d.expiry_date).toLocaleString() : 'Lifetime';
        const location = (d.remote_lat && d.remote_lng) ? `${d.remote_lat.toFixed(4)}, ${d.remote_lng.toFixed(4)}` : '<span class="text-muted">Default</span>';
        const lastSeen = d.last_seen ? new Date(d.last_seen).toLocaleTimeString() : 'N/A';

        return `
            <tr>
                <td>${index + 1}</td>
                <td class="fw-bold text-dark">${escapeHtml(d.user_tag || 'New Device')}</td>
                <td><code class="hwid-code">${escapeHtml(d.hwid)}</code></td>
                <td><span class="badge-pill badge-${d.status}">${d.status}</span></td>
                <td><small class="text-secondary">${expiry}</small></td>
                <td><small class="text-secondary">${location}</small></td>
                <td><small class="text-muted">${lastSeen}</small></td>
                <td>
                    <button class="btn-act btn-act-edit me-1" onclick="openEditModal(${d.id}, '${escapeJs(d.user_tag)}', '${d.status}', '${d.expiry_date || ''}')">Edit</button>
                    <button class="btn-act btn-act-approve me-1" onclick="quickUpdateStatus(${d.id}, 'APPROVED')">Approve</button>
                    <button class="btn-act btn-act-block me-1" onclick="quickUpdateStatus(${d.id}, 'BLOCKED')">Block</button>
                    <button class="btn-act btn-act-loc" onclick="openLocationModal(${d.id}, ${d.remote_lat || 'null'}, ${d.remote_lng || 'null'})">Location</button>
                </td>
            </tr>
        `;
    }).join('');
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
