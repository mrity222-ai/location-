const http = require('http');

const BASE_URL = 'http://localhost:3000';

let testsPassed = 0;
let testsFailed = 0;

function assert(condition, message) {
    if (condition) {
        console.log(`  ✅ PASS: ${message}`);
        testsPassed++;
    } else {
        console.error(`  ❌ FAIL: ${message}`);
        testsFailed++;
    }
}

function request(method, path, body = null, headers = {}) {
    return new Promise((resolve, reject) => {
        const url = new URL(path, BASE_URL);
        const options = {
            method: method,
            hostname: url.hostname,
            port: url.port,
            path: url.pathname + url.search,
            headers: {
                'Content-Type': 'application/json',
                ...headers
            }
        };

        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => {
                let parsed = null;
                try {
                    parsed = JSON.parse(data);
                } catch (e) {
                    parsed = data;
                }
                resolve({ status: res.statusCode, headers: res.headers, body: parsed });
            });
        });

        req.on('error', (err) => reject(err));

        if (body) {
            req.write(JSON.stringify(body));
        }
        req.end();
    });
}

async function runTests() {
    console.log('====================================================');
    console.log('🧪 RUNNING AUTOMATED INTEGRATION TESTS (VPS SERVER)');
    console.log('====================================================\n');

    try {
        // Test 1: Admin Panel Login Page (GET /admin/)
        console.log('🔹 Test 1: Admin Panel HTML Route');
        const resHtml = await request('GET', '/admin/');
        assert(resHtml.status === 200, 'GET /admin/ returns 200 OK');
        assert(typeof resHtml.body === 'string' && resHtml.body.includes('LocationSpoofer Admin'), 'HTML contains application title');

        // Test 2: Admin Login with Valid Credentials
        console.log('\n🔹 Test 2: Admin Login (Valid Credentials)');
        const loginRes = await request('POST', '/api/admin/login', { username: 'admin', password: 'admin123' });
        assert(loginRes.status === 200, 'Valid login returns 200 OK');
        assert(loginRes.body && loginRes.body.token, 'Login returns JWT token');
        const token = loginRes.body ? loginRes.body.token : null;

        // Test 3: Admin Login with Invalid Credentials
        console.log('\n🔹 Test 3: Admin Login (Invalid Credentials)');
        const invalidLoginRes = await request('POST', '/api/admin/login', { username: 'admin', password: 'wrongpassword' });
        assert(invalidLoginRes.status === 401, 'Invalid login returns 401 Unauthorized');

        // Test 4: Register Mobile Device
        console.log('\n🔹 Test 4: Device Registration Endpoint');
        const testHwid = 'TEST-HWID-' + Date.now();
        const regRes = await request('POST', '/api/device/register', { hwid: testHwid, user_tag: 'Automated Test Phone' });
        assert(regRes.status === 200, 'POST /api/device/register returns 200 OK');
        assert(regRes.body && regRes.body.hwid === testHwid, 'Device registration returns correct HWID');

        // Test 5: Check Mobile Device License Status
        console.log('\n🔹 Test 5: Device License Status Endpoint');
        const checkRes = await request('POST', '/api/device/check-status', { hwid: testHwid });
        assert(checkRes.status === 200, 'POST /api/device/check-status returns 200 OK');
        assert(checkRes.body && (checkRes.body.status === 'PENDING' || checkRes.body.status === 'APPROVED'), 'Device returns status');

        // Test 6: Protected Admin Endpoint without Token
        console.log('\n🔹 Test 6: Protected API without Token');
        const unauthRes = await request('GET', '/api/admin/stats');
        assert(unauthRes.status === 401 || unauthRes.status === 403, 'GET /api/admin/stats without token is rejected');

        // Test 7: Protected Admin Endpoint with Valid Token
        console.log('\n🔹 Test 7: Protected API with Valid Token');
        if (token) {
            const statsRes = await request('GET', '/api/admin/stats', null, { 'Authorization': `Bearer ${token}` });
            assert(statsRes.status === 200, 'GET /api/admin/stats returns 200 OK with valid token');
            assert(statsRes.body && typeof statsRes.body.total === 'number', 'Stats returns total device count');
        }

        // Test 8: Admin Activity Logs Endpoint
        console.log('\n🔹 Test 8: Admin Activity Logs Endpoint');
        if (token) {
            const logsRes = await request('GET', '/api/admin/logs', null, { 'Authorization': `Bearer ${token}` });
            assert(logsRes.status === 200, 'GET /api/admin/logs returns 200 OK');
            assert(Array.isArray(logsRes.body), 'Logs endpoint returns array of activity logs');
        }

        // Test 9: Forgot Password Endpoint
        console.log('\n🔹 Test 9: Forgot Password Endpoint');
        const forgotRes = await request('POST', '/api/admin/forgot-password', { email: 'admin@locationsofi.com' });
        assert(forgotRes.status === 200, 'POST /api/admin/forgot-password returns 200 OK');

        // Test 10: System Settings Endpoint (.env Editor)
        console.log('\n🔹 Test 10: System Settings API (.env Config)');
        if (token) {
            const settingsRes = await request('GET', '/api/admin/settings', null, { 'Authorization': `Bearer ${token}` });
            assert(settingsRes.status === 200, 'GET /api/admin/settings returns 200 OK');
            assert(settingsRes.body && settingsRes.body.smtp_host, 'Settings API returns SMTP host configuration');
        }

        // Test 11: Dedicated Devices Page (GET /admin/devices)
        console.log('\n🔹 Test 11: Dedicated Devices HTML Route');
        const devPageRes = await request('GET', '/admin/devices');
        assert(devPageRes.status === 200, 'GET /admin/devices returns 200 OK');

        // Test 12: Dedicated Activity Logs Page (GET /admin/logs)
        console.log('\n🔹 Test 12: Dedicated Activity Logs HTML Route');
        const logPageRes = await request('GET', '/admin/logs');
        assert(logPageRes.status === 200, 'GET /admin/logs returns 200 OK');

        // Test 13: User Registration Endpoint
        console.log('\n🔹 Test 13: User Registration Endpoint');
        const testUserEmail = `testuser_${Date.now()}@example.com`;
        const userRegRes = await request('POST', '/api/user/register', {
            name: 'Test User',
            email: testUserEmail,
            password: 'UserPassword123',
            hwid: 'DEV-TEST-' + Date.now()
        });
        assert(userRegRes.status === 200, 'POST /api/user/register returns 200 OK');
        assert(userRegRes.body && userRegRes.body.token, 'User registration returns auth token');

        // Test 14: User Password Login Endpoint
        console.log('\n🔹 Test 14: User Password Login Endpoint');
        const passLoginRes = await request('POST', '/api/user/login-password', {
            email: testUserEmail,
            password: 'UserPassword123',
            hwid: 'DEV-TEST-' + Date.now()
        });
        assert(passLoginRes.status === 200, 'POST /api/user/login-password returns 200 OK');
        assert(passLoginRes.body && passLoginRes.body.token, 'Password login returns token');

        // Test 15: Send OTP Endpoint
        console.log('\n🔹 Test 15: User Send OTP Endpoint');
        const sendOtpRes = await request('POST', '/api/user/send-otp', {
            email: testUserEmail
        });
        assert(sendOtpRes.status === 200, 'POST /api/user/send-otp returns 200 OK');
        const generatedOtp = sendOtpRes.body ? sendOtpRes.body.otp : null;

        // Test 16: OTP Login Endpoint
        if (generatedOtp) {
            console.log('\n🔹 Test 16: User OTP Login Endpoint');
            const otpLoginRes = await request('POST', '/api/user/login-otp', {
                email: testUserEmail,
                otp: generatedOtp,
                hwid: 'DEV-TEST-' + Date.now()
            });
            assert(otpLoginRes.body && otpLoginRes.body.token, 'OTP login returns token');
        }

        // Test 17: User Change Password Endpoint
        console.log('\n🔹 Test 17: User Change Password Endpoint');
        const changePassRes = await request('POST', '/api/user/change-password', {
            email: testUserEmail,
            current_password: 'UserPassword123',
            new_password: 'NewTestPassword456!'
        });
        assert(changePassRes.status === 200, 'POST /api/user/change-password returns 200 OK');

        console.log('\n====================================================');
        console.log(`📊 TEST SUMMARY: ${testsPassed} Passed, ${testsFailed} Failed`);
        console.log('====================================================\n');

        if (testsFailed > 0) {
            process.exit(1);
        } else {
            process.exit(0);
        }
    } catch (err) {
        console.error('❌ FATAL TEST ERROR:', err);
        process.exit(1);
    }
}

runTests();
