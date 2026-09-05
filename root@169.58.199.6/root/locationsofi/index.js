const http = require('http');
const express = require('express');
const cors = require('cors');
const sqlite3 = require('sqlite3').verbose();
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const nodemailer = require('nodemailer');
const path = require('path');
const WebSocket = require('ws');
const swaggerUi = require('swagger-ui-express');
const swaggerDocument = require('./swagger.json');

const { rateLimit } = require('express-rate-limit');

const app = express();
const server = http.createServer(app);
const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'locationsofi_super_secret_jwt_key_2026';

// WebSocket Server Setup
const wss = new WebSocket.Server({ server, path: '/ws' });
const activeSockets = new Map();

wss.on('connection', (ws, req) => {
    try {
        const host = req.headers.host || 'localhost';
        const url = new URL(req.url, `http://${host}`);
        const hwid = url.searchParams.get('hwid');

        if (hwid) {
            ws.hwid = hwid;
            activeSockets.set(hwid, ws);
            console.log(`🔌 [WebSocket] Device connected: ${hwid}`);
            ws.send(JSON.stringify({ type: 'CONNECTED', message: 'WebSocket real-time link established' }));
        }
    } catch (e) {
        console.error('WebSocket connection error:', e.message);
    }

    ws.on('close', () => {
        if (ws.hwid) {
            activeSockets.delete(ws.hwid);
            console.log(`🔌 [WebSocket] Device disconnected: ${ws.hwid}`);
        }
    });

    ws.on('error', (err) => {
        console.error('WebSocket error:', err.message);
    });
});

function broadcastToDevice(hwid, type, data) {
    const ws = activeSockets.get(hwid);
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type, payload: data }));
        return true;
    }
    return false;
}

// Swagger OpenAPI Documentation
app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerDocument));

// SMTP Configuration
const SMTP_HOST = process.env.SMTP_HOST || 'smtp.gmail.com';
const SMTP_PORT = parseInt(process.env.SMTP_PORT || '587');
const SMTP_USER = process.env.SMTP_USER || '';
const SMTP_PASS = process.env.SMTP_PASS || '';
const SMTP_FROM = process.env.SMTP_FROM || 'LocationSpoofer Admin <no-reply@locationsofi.com>';

const transporter = nodemailer.createTransport({
    host: SMTP_HOST,
    port: SMTP_PORT,
    secure: SMTP_PORT === 465,
    auth: (SMTP_USER && SMTP_PASS) ? { user: SMTP_USER, pass: SMTP_PASS } : undefined
});

// --- RATE LIMITERS ---
const globalLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    limit: 100,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    message: { error: 'Too many requests from this IP, please try again after 15 minutes.' }
});

const authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    limit: 10,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    message: { error: 'Too many login attempts. Please try again after 15 minutes.' }
});

const deviceCheckLimiter = rateLimit({
    windowMs: 1 * 60 * 1000,
    limit: 30,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    message: { error: 'Device checking limit exceeded. Please wait a moment.' }
});

app.use('/api/', globalLimiter);
app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Database setup
const db = new sqlite3.Database(path.join(__dirname, 'vps_license.db'), (err) => {
    if (err) {
        console.error('Failed to open database:', err.message);
    } else {
        console.log('Connected to SQLite database.');
        initDb();
    }
});

function initDb() {
    db.serialize(() => {
        db.run(`
            CREATE TABLE IF NOT EXISTS admins (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                email TEXT UNIQUE,
                password_hash TEXT NOT NULL,
                reset_token TEXT NULL,
                reset_token_expiry DATETIME NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        `);

        db.run(`
            CREATE TABLE IF NOT EXISTS devices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                hwid TEXT UNIQUE NOT NULL,
                user_tag TEXT DEFAULT 'New User',
                status TEXT DEFAULT 'PENDING',
                expiry_date DATETIME NULL,
                remote_lat REAL NULL,
                remote_lng REAL NULL,
                last_seen DATETIME DEFAULT CURRENT_TIMESTAMP,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        `);

        db.run(`
            CREATE TABLE IF NOT EXISTS activity_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action TEXT NOT NULL,
                details TEXT,
                admin_username TEXT,
                ip_address TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        `);

        db.run(`
            CREATE TABLE IF NOT EXISTS system_settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        `);

        // Insert default admin: admin / admin123
        const defaultHash = bcrypt.hashSync('admin123', 10);
        db.run(
            `INSERT OR IGNORE INTO admins (id, username, email, password_hash) VALUES (1, 'admin', 'admin@locationsofi.com', ?)`,
            [defaultHash]
        );
        db.run(`UPDATE admins SET email = 'admin@locationsofi.com' WHERE username = 'admin' AND (email IS NULL OR email = '')`);
    });
}

function logActivity(action, details, adminUsername = 'System', ipAddress = '') {
    db.run(
        `INSERT INTO activity_logs (action, details, admin_username, ip_address) VALUES (?, ?, ?, ?)`,
        [action, details, adminUsername, ipAddress]
    );
}

// Middleware: Authenticate Admin JWT
function authenticateAdmin(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];
    if (!token) return res.status(401).json({ error: 'Access token required' });

    jwt.verify(token, JWT_SECRET, (err, user) => {
        if (err) return res.status(403).json({ error: 'Invalid or expired token' });
        req.user = user;
        next();
    });
}

// --- CLIENT APIS (ANDROID APP) ---

// 1. Register Device
app.post('/api/device/register', (req, res) => {
    const { hwid, user_tag } = req.body;
    if (!hwid) return res.status(400).json({ error: 'hwid is required' });

    const tag = user_tag || 'New Device';
    db.run(
        `INSERT OR IGNORE INTO devices (hwid, user_tag, status) VALUES (?, ?, 'PENDING')`,
        [hwid, tag],
        function(err) {
            if (err) return res.status(500).json({ error: err.message });
            if (this.changes > 0) {
                logActivity('DEVICE_AUTO_REGISTERED', `New device registered: ${hwid} (${tag})`, 'Device', req.ip);
            }
            res.json({ message: 'Registration received', hwid });
        }
    );
});

// 2. Check Device License Status
app.post('/api/device/check-status', deviceCheckLimiter, (req, res) => {
    const { hwid } = req.body;
    if (!hwid) return res.status(400).json({ error: 'hwid is required' });

    db.get(`SELECT * FROM devices WHERE hwid = ?`, [hwid], (err, row) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!row) {
            db.run(`INSERT INTO devices (hwid, user_tag, status) VALUES (?, 'New Device', 'PENDING')`, [hwid]);
            logActivity('DEVICE_AUTO_REGISTERED', `New device registered: ${hwid}`, 'Device', req.ip);
            return res.json({ status: 'PENDING', message: 'Device registered, pending admin approval' });
        }

        db.run(`UPDATE devices SET last_seen = CURRENT_TIMESTAMP WHERE hwid = ?`, [hwid]);

        if (row.expiry_date) {
            const expiryTime = new Date(row.expiry_date).getTime();
            if (Date.now() > expiryTime && row.status !== 'EXPIRED') {
                db.run(`UPDATE devices SET status = 'EXPIRED' WHERE hwid = ?`, [hwid]);
                row.status = 'EXPIRED';
                logActivity('DEVICE_AUTO_EXPIRED', `Device license expired: ${hwid} (${row.user_tag})`, 'System', req.ip);
            }
        }

        res.json({
            hwid: row.hwid,
            user_tag: row.user_tag,
            status: row.status,
            expiry_date: row.expiry_date,
            remote_lat: row.remote_lat,
            remote_lng: row.remote_lng
        });
    });
});

// --- ADMIN APIS & AUTHENTICATION ---

// Admin Login
app.post('/api/admin/login', authLimiter, (req, res) => {
    const { username, password } = req.body;
    if (!username || !password) return res.status(400).json({ error: 'Username and password required' });

    db.get(`SELECT * FROM admins WHERE username = ? OR email = ?`, [username, username], (err, admin) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!admin || !bcrypt.compareSync(password, admin.password_hash)) {
            logActivity('ADMIN_LOGIN_FAILED', `Failed login attempt for user: ${username}`, 'Unknown', req.ip);
            return res.status(401).json({ error: 'Invalid credentials' });
        }

        const token = jwt.sign({ id: admin.id, username: admin.username }, JWT_SECRET, { expiresIn: '24h' });
        logActivity('ADMIN_LOGIN_SUCCESS', `Admin logged in: ${admin.username}`, admin.username, req.ip);
        res.json({ token, username: admin.username, email: admin.email });
    });
});

// Forgot Password Request
app.post('/api/admin/forgot-password', authLimiter, (req, res) => {
    const { email } = req.body;
    if (!email) return res.status(400).json({ error: 'Email address is required' });

    db.get(`SELECT * FROM admins WHERE email = ? OR username = ?`, [email, email], (err, admin) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!admin) {
            return res.json({ message: 'If that email is registered, a password reset link has been sent.' });
        }

        const resetToken = crypto.randomBytes(32).toString('hex');
        const tokenExpiry = new Date(Date.now() + 3600000).toISOString();

        db.run(
            `UPDATE admins SET reset_token = ?, reset_token_expiry = ? WHERE id = ?`,
            [resetToken, tokenExpiry, admin.id],
            async function(err) {
                if (err) return res.status(500).json({ error: err.message });

                const host = req.get('host');
                const protocol = req.protocol;
                const resetUrl = `${protocol}://${host}/admin/reset-password?token=${resetToken}`;

                logActivity('PASSWORD_RESET_REQUESTED', `Password reset token generated for ${admin.username}`, admin.username, req.ip);

                const mailOptions = {
                    from: SMTP_FROM,
                    to: admin.email || email,
                    subject: '🔑 LocationSpoofer Admin - Password Reset Request',
                    html: `
                        <div style="font-family: Arial, sans-serif; background: #0f172a; color: #f8fafc; padding: 24px; border-radius: 8px;">
                            <h2 style="color: #3b82f6;">LocationSpoofer VPS Admin Password Reset</h2>
                            <p>You requested a password reset for your Admin Panel account (<strong>${admin.username}</strong>).</p>
                            <p>Click the link below to set a new password. This link is valid for <strong>1 hour</strong>:</p>
                            <div style="margin: 20px 0;">
                                <a href="${resetUrl}" style="background: #2563eb; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;">Reset Password Now</a>
                            </div>
                            <p style="color: #94a3b8; font-size: 12px;">Or copy and paste this URL into your browser:<br><a href="${resetUrl}" style="color: #60a5fa;">${resetUrl}</a></p>
                        </div>
                    `
                };

                try {
                    if (SMTP_USER && SMTP_PASS) {
                        await transporter.sendMail(mailOptions);
                    }
                    res.json({ message: 'If that email is registered, a password reset link has been sent.', resetUrl: (SMTP_USER ? undefined : resetUrl) });
                } catch (sendErr) {
                    res.json({
                        message: 'Reset token generated (SMTP credentials missing on server). Use local reset link below:',
                        resetUrl: resetUrl
                    });
                }
            }
        );
    });
});

// Reset Password Execution
app.post('/api/admin/reset-password', (req, res) => {
    const { token, new_password } = req.body;
    if (!token || !new_password) return res.status(400).json({ error: 'Token and new password are required' });

    db.get(`SELECT * FROM admins WHERE reset_token = ?`, [token], (err, admin) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!admin) return res.status(400).json({ error: 'Invalid or expired password reset token' });

        const newHash = bcrypt.hashSync(new_password, 10);
        db.run(
            `UPDATE admins SET password_hash = ?, reset_token = NULL, reset_token_expiry = NULL WHERE id = ?`,
            [newHash, admin.id],
            function(err) {
                if (err) return res.status(500).json({ error: err.message });
                logActivity('PASSWORD_RESET_SUCCESS', `Password successfully reset for admin ${admin.username}`, admin.username, req.ip);
                res.json({ message: 'Password reset successful! You can now login with your new password.' });
            }
        );
    });
});

// Dashboard Overview Stats
app.get('/api/admin/stats', authenticateAdmin, (req, res) => {
    db.all(`SELECT status, COUNT(*) as count FROM devices GROUP BY status`, [], (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        const stats = { total: 0, APPROVED: 0, PENDING: 0, BLOCKED: 0, EXPIRED: 0 };
        rows.forEach(r => {
            stats[r.status] = r.count;
            stats.total += r.count;
        });
        res.json(stats);
    });
});

// Get All Devices List
app.get('/api/admin/devices', authenticateAdmin, (req, res) => {
    db.all(`SELECT * FROM devices ORDER BY created_at DESC`, [], (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json(rows);
    });
});

// Get Activity Logs List
app.get('/api/admin/logs', authenticateAdmin, (req, res) => {
    db.all(`SELECT * FROM activity_logs ORDER BY created_at DESC LIMIT 50`, [], (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json(rows);
    });
});

// Update Device Status & Expiry
app.post('/api/admin/device/update-status', authenticateAdmin, (req, res) => {
    const { id, status, user_tag, expiry_date } = req.body;
    if (!id || !status) return res.status(400).json({ error: 'id and status required' });

    db.get(`SELECT * FROM devices WHERE id = ?`, [id], (err, device) => {
        const hwid = device ? device.hwid : id;
        db.run(
            `UPDATE devices SET status = ?, user_tag = COALESCE(?, user_tag), expiry_date = ? WHERE id = ?`,
            [status, user_tag, expiry_date || null, id],
            function(err) {
                if (err) return res.status(500).json({ error: err.message });
                logActivity(
                    'DEVICE_STATUS_UPDATED',
                    `Device #${id} (${hwid}) status set to ${status}${user_tag ? ' (Tag: ' + user_tag + ')' : ''}`,
                    req.user.username,
                    req.ip
                );
                // Broadcast to WebSocket client if connected
                broadcastToDevice(hwid, 'STATUS_UPDATE', { status, expiry_date });
                res.json({ message: 'Device updated successfully' });
            }
        );
    });
});

// Set Remote Location Override
app.post('/api/admin/device/set-location', authenticateAdmin, (req, res) => {
    const { id, remote_lat, remote_lng } = req.body;
    if (!id) return res.status(400).json({ error: 'id required' });

    db.get(`SELECT * FROM devices WHERE id = ?`, [id], (err, device) => {
        const hwid = device ? device.hwid : null;
        db.run(
            `UPDATE devices SET remote_lat = ?, remote_lng = ? WHERE id = ?`,
            [remote_lat, remote_lng, id],
            function(err) {
                if (err) return res.status(500).json({ error: err.message });
                logActivity(
                    'REMOTE_LOCATION_SET',
                    `Remote location override set for Device #${id}: ${remote_lat}, ${remote_lng}`,
                    req.user.username,
                    req.ip
                );
                // Broadcast to WebSocket client if connected
                if (hwid) {
                    broadcastToDevice(hwid, 'LOCATION_OVERRIDE', { remote_lat, remote_lng });
                }
                res.json({ message: 'Remote location updated successfully' });
            }
        );
    });
});

// --- SYSTEM SETTINGS APIS (.env VISUAL CONFIG EDITOR) ---

// Get System Settings
app.get('/api/admin/settings', authenticateAdmin, (req, res) => {
    db.all(`SELECT * FROM system_settings`, [], (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        const settings = {
            smtp_host: SMTP_HOST,
            smtp_port: SMTP_PORT,
            smtp_user: SMTP_USER,
            smtp_pass: SMTP_PASS,
            smtp_from: SMTP_FROM,
            jwt_secret: JWT_SECRET,
            default_device_status: 'PENDING',
            default_expiry_days: '30'
        };
        rows.forEach(r => { settings[r.key] = r.value; });
        res.json(settings);
    });
});

// Update System Settings & Change Password
app.post('/api/admin/settings', authenticateAdmin, (req, res) => {
    const { smtp_host, smtp_port, smtp_user, smtp_pass, smtp_from, jwt_secret, default_device_status, default_expiry_days, current_password, new_password } = req.body;

    const updates = [
        ['smtp_host', smtp_host || 'smtp.gmail.com'],
        ['smtp_port', smtp_port || '587'],
        ['smtp_user', smtp_user || ''],
        ['smtp_pass', smtp_pass || ''],
        ['smtp_from', smtp_from || 'LocationSpoofer Admin <no-reply@locationsofi.com>'],
        ['jwt_secret', jwt_secret || JWT_SECRET],
        ['default_device_status', default_device_status || 'PENDING'],
        ['default_expiry_days', default_expiry_days || '30']
    ];

    db.serialize(() => {
        const stmt = db.prepare(`INSERT OR REPLACE INTO system_settings (key, value) VALUES (?, ?)`);
        updates.forEach(([k, v]) => stmt.run(k, String(v)));
        stmt.finalize();

        // Handle Admin Password Change if provided
        if (new_password) {
            if (!current_password) return res.status(400).json({ error: 'Current password is required to change password' });
            db.get(`SELECT * FROM admins WHERE username = ?`, [req.user.username], (err, admin) => {
                if (err || !admin || !bcrypt.compareSync(current_password, admin.password_hash)) {
                    return res.status(400).json({ error: 'Incorrect current password' });
                }
                const newHash = bcrypt.hashSync(new_password, 10);
                db.run(`UPDATE admins SET password_hash = ? WHERE id = ?`, [newHash, admin.id]);
                logActivity('ADMIN_PASSWORD_CHANGED', `Password updated for admin ${admin.username}`, req.user.username, req.ip);
            });
        }

        logActivity('SETTINGS_UPDATED', 'System & SMTP settings updated by admin', req.user.username, req.ip);
        res.json({ message: 'Settings saved successfully' });
    });
});

// Test SMTP Email Endpoint
app.post('/api/admin/test-smtp', authenticateAdmin, async (req, res) => {
    const { target_email } = req.body;
    const emailToUse = target_email || req.user.email || 'admin@locationsofi.com';

    try {
        const mailOptions = {
            from: SMTP_FROM,
            to: emailToUse,
            subject: '✅ LocationSpoofer Admin - Test SMTP Email',
            html: `<div style="font-family: sans-serif; background: #f4f6fc; padding: 24px; border-radius: 12px;">
                <h2 style="color: #2563eb;">🛰️ LocationSpoofer SMTP Test</h2>
                <p>Your SMTP Email configuration is working perfectly!</p>
                <p>Sent at: <strong>${new Date().toLocaleString()}</strong></p>
            </div>`
        };
        await transporter.sendMail(mailOptions);
        logActivity('SMTP_TEST_SUCCESS', `Test SMTP email sent to ${emailToUse}`, req.user.username, req.ip);
        res.json({ message: `Test email sent successfully to ${emailToUse}` });
    } catch (e) {
        logActivity('SMTP_TEST_FAILED', `Failed test email to ${emailToUse}: ${e.message}`, req.user.username, req.ip);
        res.status(500).json({ error: `SMTP Error: ${e.message}` });
    }
});

// Serve Admin Pages
app.get('/admin', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'admin', 'login.html'));
});
app.get('/admin/reset-password', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'admin', 'reset-password.html'));
});
app.get('/admin/dashboard', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'admin', 'dashboard.html'));
});
app.get('/admin/devices', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'admin', 'devices.html'));
});
app.get('/admin/logs', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'admin', 'logs.html'));
});
app.get('/admin/settings', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'admin', 'settings.html'));
});

server.listen(PORT, () => {
    console.log(`LocationSpoofer VPS Server running on http://localhost:${PORT}`);
    console.log(`Admin Panel available at http://localhost:${PORT}/admin`);
    console.log(`Swagger API Docs available at http://localhost:${PORT}/api-docs`);
    console.log(`WebSocket Real-Time Server listening on ws://localhost:${PORT}/ws`);
});