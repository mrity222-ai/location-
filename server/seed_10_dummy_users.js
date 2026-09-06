const db = require('./db');
const bcrypt = require('bcryptjs');

const dummyData = [
  { name: 'Amit Sharma', email: 'amit.sharma@example.com', hwid: 'HWID-DUMMY-001', status: 'APPROVED', lat: 28.6139, lng: 77.2090, expiry: '2026-12-31 23:59:59' },
  { name: 'Priya Verma', email: 'priya.verma@example.com', hwid: 'HWID-DUMMY-002', status: 'PENDING', lat: 19.0760, lng: 72.8777, expiry: '2026-10-15 23:59:59' },
  { name: 'Rahul Singh', email: 'rahul.singh@example.com', hwid: 'HWID-DUMMY-003', status: 'BLOCKED', lat: 12.9716, lng: 77.5946, expiry: '2026-08-01 00:00:00' },
  { name: 'Neha Gupta', email: 'neha.gupta@example.com', hwid: 'HWID-DUMMY-004', status: 'EXPIRED', lat: 13.0827, lng: 80.2707, expiry: '2025-01-01 00:00:00' },
  { name: 'Vikram Patel', email: 'vikram.patel@example.com', hwid: 'HWID-DUMMY-005', status: 'APPROVED', lat: 22.5726, lng: 88.3639, expiry: '2027-01-01 00:00:00' },
  { name: 'Ananya Roy', email: 'ananya.roy@example.com', hwid: 'HWID-DUMMY-006', status: 'PENDING', lat: 17.3850, lng: 78.4867, expiry: '2026-11-30 23:59:59' },
  { name: 'Rohan Mehta', email: 'rohan.mehta@example.com', hwid: 'HWID-DUMMY-007', status: 'APPROVED', lat: 23.0225, lng: 72.5714, expiry: '2026-09-30 23:59:59' },
  { name: 'Sonia Kapoor', email: 'sonia.kapoor@example.com', hwid: 'HWID-DUMMY-008', status: 'BLOCKED', lat: 26.9124, lng: 75.7873, expiry: '2026-12-01 00:00:00' },
  { name: 'Karan Joshi', email: 'karan.joshi@example.com', hwid: 'HWID-DUMMY-009', status: 'EXPIRED', lat: 30.7333, lng: 76.7794, expiry: '2024-12-31 23:59:59' },
  { name: 'Pooja Reddy', email: 'pooja.reddy@example.com', hwid: 'HWID-DUMMY-010', status: 'APPROVED', lat: 15.3173, lng: 75.7139, expiry: '2026-12-31 23:59:59' }
];

async function seed() {
  const hash = await bcrypt.hash('Password123!', 10);
  
  for (const item of dummyData) {
    await new Promise((resolve, reject) => {
      db.run(
        `INSERT OR REPLACE INTO users (name, email, password_hash, hwid) VALUES (?, ?, ?, ?)`,
        [item.name, item.email, hash, item.hwid],
        (err) => {
          if (err) return reject(err);
          resolve();
        }
      );
    });

    await new Promise((resolve, reject) => {
      db.run(
        `INSERT OR REPLACE INTO devices (hwid, user_tag, status, expiry_date, remote_lat, remote_lng, last_seen) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)`,
        [item.hwid, item.name, item.status, item.expiry, item.lat, item.lng],
        (err) => {
          if (err) return reject(err);
          resolve();
        }
      );
    });
    console.log(`Seeded: ${item.name} (${item.email}) - Status: ${item.status}`);
  }
}

seed().then(() => {
  console.log('✅ 10 Dummy Records successfully seeded!');
  process.exit(0);
}).catch(err => {
  console.error('❌ Error seeding records:', err);
  process.exit(1);
});
