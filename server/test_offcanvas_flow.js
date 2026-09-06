const http = require('http');

function request(options, bodyData = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          resolve({ statusCode: res.statusCode, data: parsed });
        } catch (e) {
          resolve({ statusCode: res.statusCode, data: data });
        }
      });
    });
    req.on('error', reject);
    if (bodyData) {
      req.write(typeof bodyData === 'string' ? bodyData : JSON.stringify(bodyData));
    }
    req.end();
  });
}

async function runVerification() {
  console.log('--- 0. Logging in as Admin ---');
  const loginRes = await request({
    hostname: 'localhost',
    port: 3000,
    path: '/api/admin/login',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { username: 'admin', password: 'admin123' });

  const token = loginRes.data && loginRes.data.token;
  console.log(`Admin Login success: ${loginRes.statusCode === 200}, Token received: ${!!token}`);

  const authHeaders = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  };

  console.log('\n--- 1. Fetching all devices ---');
  const getRes = await request({
    hostname: 'localhost',
    port: 3000,
    path: '/api/admin/devices',
    method: 'GET',
    headers: authHeaders
  });

  console.log(`GET /api/admin/devices response status: ${getRes.statusCode}`);
  const devices = getRes.data.devices || getRes.data;
  console.log(`Fetched ${Array.isArray(devices) ? devices.length : 0} devices.`);

  const dummy1 = (devices || []).find(d => d.hwid === 'HWID-DUMMY-002'); // Priya Verma (PENDING)
  console.log('Priya Verma initial status:', dummy1 ? dummy1.status : 'not found');

  console.log('\n--- 2. Approving Priya Verma (HWID-DUMMY-002) ---');
  const priyaInit = (devices || []).find(d => d.hwid === 'HWID-DUMMY-002');
  const approveRes = await request({
    hostname: 'localhost',
    port: 3000,
    path: '/api/admin/device/update-status',
    method: 'POST',
    headers: authHeaders
  }, {
    id: priyaInit.id,
    status: 'APPROVED',
    expiry_date: '2026-12-31 23:59:59'
  });
  console.log('Approve response:', approveRes.data);

  console.log('\n--- 3. Setting Mock Location for Rohan Mehta (HWID-DUMMY-007) ---');
  const rohanInit = (devices || []).find(d => d.hwid === 'HWID-DUMMY-007');
  const locRes = await request({
    hostname: 'localhost',
    port: 3000,
    path: '/api/admin/device/set-location',
    method: 'POST',
    headers: authHeaders
  }, {
    id: rohanInit.id,
    remote_lat: 28.7041,
    remote_lng: 77.1025
  });
  console.log('Location update response:', locRes.data);

  console.log('\n--- 4. Editing User Tag for Ananya Roy (HWID-DUMMY-006) ---');
  const ananyaInit = (devices || []).find(d => d.hwid === 'HWID-DUMMY-006');
  const editRes = await request({
    hostname: 'localhost',
    port: 3000,
    path: '/api/admin/device/update-status',
    method: 'POST',
    headers: authHeaders
  }, {
    id: ananyaInit.id,
    status: ananyaInit.status,
    user_tag: 'Ananya Roy (VIP)'
  });
  console.log('Edit user tag response:', editRes.data);

  console.log('\n--- 5. Verifying DB persistence via GET /api/admin/devices ---');
  const verifyRes = await request({
    hostname: 'localhost',
    port: 3000,
    path: '/api/admin/devices',
    method: 'GET',
    headers: authHeaders
  });

  const updatedDevices = verifyRes.data.devices || verifyRes.data;
  const priya = updatedDevices.find(d => d.hwid === 'HWID-DUMMY-002');
  const rohan = updatedDevices.find(d => d.hwid === 'HWID-DUMMY-007');
  const ananya = updatedDevices.find(d => d.hwid === 'HWID-DUMMY-006');

  console.log('Priya Verma updated status:', priya ? priya.status : 'N/A');
  console.log('Rohan Mehta updated lat/lng:', rohan ? `${rohan.remote_lat}, ${rohan.remote_lng}` : 'N/A');
  console.log('Ananya Roy updated tag & registered name:', ananya ? `${ananya.user_tag} / registered: ${ananya.registered_name}` : 'N/A');

  if (priya?.status === 'APPROVED' && rohan?.remote_lat === 28.7041 && ananya?.user_tag === 'Ananya Roy (VIP)' && ananya?.registered_name === 'Ananya Roy (VIP)') {
    console.log('\n✅ ALL Offcanvas Panel & Admin Actions Verified Successfully!');
  } else {
    console.error('\n❌ Verification failed!');
  }
}

runVerification().catch(console.error);
