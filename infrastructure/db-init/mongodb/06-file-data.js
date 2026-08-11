// Restore file-service metadata so all film/actor/director thumbnails continue
// resolving after a database reset. The JSON file is a mongoexport snapshot.
(function seedFileMetadata() {
  var fileSeedFs = require('fs');
  var fileDb = db.getSiblingDB('file-service');
  var snapshotPath = '/docker-entrypoint-initdb.d/06-file-metadata.json';
  var exportedDocuments = EJSON.parse(fileSeedFs.readFileSync(snapshotPath, 'utf8'));
  var stableOwnerId = '10000000-0000-0000-0000-000000000001';

  exportedDocuments.forEach(function (document) {
    document.ownerId = stableOwnerId;
    fileDb.file_mgmt.replaceOne({ _id: document._id }, document, { upsert: true });
  });

  print('Seeded file metadata: ' + exportedDocuments.length);
})();
