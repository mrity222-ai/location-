const sqlite3 = require('sqlite3').verbose();
const mysql = require('mysql2');
const path = require('path');
const bcrypt = require('bcryptjs');

const DB_TYPE = (process.env.DB_TYPE || (process.env.MYSQL_HOST || process.env.DB_HOST ? 'mysql' : 'sqlite')).toLowerCase();

let sqliteDb = null;
let mysqlPool = null;

console.log(`🗄️ Initializing Database Engine: [${DB_TYPE.toUpperCase()}]`);

if (DB_TYPE === 'mysql') {
    mysqlPool = mysql.createPool({
        host: process.env.MYSQL_HOST || process.env.DB_HOST || 'localhost',
        port: parseInt(process.env.MYSQL_PORT || process.env.DB_PORT || '3306'),
        user: process.env.MYSQL_USER || process.env.DB_USER || 'root',
        password: process.env.MYSQL_PASSWORD || process.env.DB_PASSWORD || '',
        database: process.env.MYSQL_DATABASE || process.env.DB_NAME || 'vps_license',
        waitForConnections: true,
        connectionLimit: 10,
        queueLimit: 0
    });
} else {
    sqliteDb = new sqlite3.Database(path.join(__dirname, 'vps_license.db'), (err) => {
        if (err) {
            console.error('❌ Failed to open SQLite database:', err.message);
        } else {
            console.log('✅ Connected to SQLite database.');
        }
    });
}

function normalizeSql(sql) {
    if (DB_TYPE === 'mysql') {
        return sql
            .replace(/INSERT OR IGNORE INTO/gi, 'INSERT IGNORE INTO')
            .replace(/INSERT OR REPLACE INTO/gi, 'REPLACE INTO')
            .replace(/DATETIME/gi, 'DATETIME')
            .replace(/REAL/gi, 'DOUBLE');
    }
    return sql;
}

function normalizeParams(params) {
    if (!Array.isArray(params)) return params;
    return params.map(p => {
        if (typeof p === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(p)) {
            return p.slice(0, 19).replace('T', ' ');
        }
        return p;
    });
}

const db = {
    type: DB_TYPE,
    
    get: function (sql, params = [], callback) {
        if (typeof params === 'function') {
            callback = params;
            params = [];
        }
        const querySql = normalizeSql(sql);
        const cleanParams = normalizeParams(params);

        if (DB_TYPE === 'mysql') {
            mysqlPool.query(querySql, cleanParams, (err, results) => {
                if (err) return callback(err);
                const row = results && results.length > 0 ? results[0] : null;
                callback(null, row);
            });
        } else {
            sqliteDb.get(querySql, cleanParams, callback);
        }
    },

    all: function (sql, params = [], callback) {
        if (typeof params === 'function') {
            callback = params;
            params = [];
        }
        const querySql = normalizeSql(sql);
        const cleanParams = normalizeParams(params);

        if (DB_TYPE === 'mysql') {
            mysqlPool.query(querySql, cleanParams, (err, results) => {
                if (err) return callback(err);
                callback(null, results || []);
            });
        } else {
            sqliteDb.all(querySql, cleanParams, callback);
        }
    },

    run: function (sql, params = [], callback) {
        if (typeof params === 'function') {
            callback = params;
            params = [];
        }
        const querySql = normalizeSql(sql);
        const cleanParams = normalizeParams(params);

        if (DB_TYPE === 'mysql') {
            mysqlPool.query(querySql, cleanParams, function (err, results) {
                if (err) {
                    if (callback) callback(err);
                    return;
                }
                const context = {
                    lastID: results ? results.insertId : null,
                    changes: results ? results.affectedRows : 0
                };
                if (callback) callback.call(context, null);
            });
        } else {
            sqliteDb.run(querySql, cleanParams, function (err) {
                if (callback) callback.call(this, err);
            });
        }
    },

    serialize: function (fn) {
        if (DB_TYPE === 'mysql') {
            if (fn) fn();
        } else {
            sqliteDb.serialize(fn);
        }
    }
};

function initDb() {
    if (DB_TYPE === 'mysql') {
        const createAdmins = `
            CREATE TABLE IF NOT EXISTS admins (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(100) UNIQUE NOT NULL,
                email VARCHAR(150) UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                reset_token VARCHAR(255) NULL,
                reset_token_expiry DATETIME NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        const createDevices = `
            CREATE TABLE IF NOT EXISTS devices (
                id INT AUTO_INCREMENT PRIMARY KEY,
                hwid VARCHAR(150) UNIQUE NOT NULL,
                user_tag VARCHAR(150) DEFAULT 'New User',
                status VARCHAR(50) DEFAULT 'PENDING',
                expiry_date DATETIME NULL,
                remote_lat DOUBLE NULL,
                remote_lng DOUBLE NULL,
                last_seen DATETIME DEFAULT CURRENT_TIMESTAMP,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        const createLogs = `
            CREATE TABLE IF NOT EXISTS activity_logs (
                id INT AUTO_INCREMENT PRIMARY KEY,
                action VARCHAR(100) NOT NULL,
                details TEXT,
                admin_username VARCHAR(100),
                ip_address VARCHAR(100),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        const createSettings = `
            CREATE TABLE IF NOT EXISTS system_settings (
                \`key\` VARCHAR(100) PRIMARY KEY,
                \`value\` TEXT
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        const createUsers = `
            CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(150) NOT NULL,
                email VARCHAR(150) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                otp_code VARCHAR(10) NULL,
                otp_expiry DATETIME NULL,
                hwid VARCHAR(150) NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        mysqlPool.query(createAdmins, (err) => {
            if (err) console.error('Error creating MySQL admins table:', err.message);
            const defaultHash = bcrypt.hashSync('admin123', 10);
            db.run(
                `INSERT IGNORE INTO admins (id, username, email, password_hash) VALUES (1, 'admin', 'admin@locationsofi.com', ?)`,
                [defaultHash]
            );
        });
        mysqlPool.query(createDevices, (err) => {
            if (err) console.error('Error creating MySQL devices table:', err.message);
        });
        mysqlPool.query(createLogs, (err) => {
            if (err) console.error('Error creating MySQL activity_logs table:', err.message);
        });
        mysqlPool.query(createSettings, (err) => {
            if (err) console.error('Error creating MySQL system_settings table:', err.message);
        });
        mysqlPool.query(createUsers, (err) => {
            if (err) console.error('Error creating MySQL users table:', err.message);
        });

    } else {
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

            db.run(`
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    otp_code TEXT NULL,
                    otp_expiry DATETIME NULL,
                    hwid TEXT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            `);

            const defaultHash = bcrypt.hashSync('admin123', 10);
            db.run(
                `INSERT OR IGNORE INTO admins (id, username, email, password_hash) VALUES (1, 'admin', 'admin@locationsofi.com', ?)`,
                [defaultHash]
            );
            db.run(`UPDATE admins SET email = 'admin@locationsofi.com' WHERE username = 'admin' AND (email IS NULL OR email = '')`);
        });
    }
}

initDb();

module.exports = db;
