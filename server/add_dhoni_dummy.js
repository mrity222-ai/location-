const db = require('./db');
const bcrypt = require('bcryptjs');

async function addDummy() {
  const hash = await bcrypt.hash('Password123!', 10);
  const email = 'dhoniy423@gmail.com';
  const name = 'MS Dhoni';
  const hwid = 'HWID-DHONI-007';
  const status = 'APPROVED';
  const expiry = '2027-12-31 23:59:59';
  const lat = 23.3441;
  const lng = 85.3096;

  await new Promise((resolve, reject) => {
    db.run(
      `INSERT OR REPLACE INTO users (name, email, password_hash, hwid) VALUES (?, ?, ?, ?)`,
      [name, email, hash, hwid],
      (err) => {
        if (err) return reject(err);
        resolve();
      }
    );
  });

  await new Promise((resolve, reject) => {
    db.run(
      `INSERT OR REPLACE INTO devices (hwid, user_tag, status, expiry_date, remote_lat, remote_lng, last_seen) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)`,
      [hwid, name, status, expiry, lat, lng],
      (err) => {
        if (err) return reject(err);
        resolve();
      }
    );
  });

  console.log(`✅ Successfully added dummy user: ${name} (${email})`);
}

addDummy().then(() => process.exit(0)).catch(err => {
  console.error('❌ Error adding user:', err);
  process.exit(1);
});
