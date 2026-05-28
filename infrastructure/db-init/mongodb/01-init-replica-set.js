// Initialize Replica Set for MongoDB (Required for Debezium CDC)
// Check if already initiated to avoid errors
try {
  var status = rs.status();
  print("Replica set already initiated.");
} catch (e) {
  print("Initiating replica set...");
  rs.initiate({
    _id: "rs0",
    members: [
      { _id: 0, host: "mongodb:27017" }
    ]
  });
}

// Wait for the replica set to be ready
sleep(2000);

// Create Debezium User with necessary roles for CDC
db.getSiblingDB('admin').createUser({
  user: 'debezium',
  pwd: 'REDACTED_DBZ_CREDENTIAL',
  roles: [
    { role: 'readAnyDatabase', db: 'admin' },
    { role: 'clusterMonitor', db: 'admin' },
    { role: 'read', db: 'local' }
  ]
});

print("MongoDB CDC initialization completed successfully.");
