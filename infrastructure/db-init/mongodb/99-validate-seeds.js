// Manual validation helper. It rewrites database names in memory, executes the
// real seed files against isolated validation databases, asserts invariants,
// prints a summary, then removes only those validation databases.
(function validateMongoSeeds() {
  var validationFs = require('fs');
  var seedRoot = '/docker-entrypoint-initdb.d/';
  var validationDatabases = [
    'profile-seed-validation-20260810',
    'post-seed-validation-20260810',
    'friend-seed-validation-20260810',
    'chat-seed-validation-20260810',
    'file-seed-validation-20260810'
  ];

  function assertSeed(condition, message) {
    if (!condition) {
      throw new Error('Mongo seed validation failed: ' + message);
    }
  }

  function executeSeed(fileName, sourceDatabase, validationDatabase) {
    var source = validationFs.readFileSync(seedRoot + fileName, 'utf8');
    var rewritten = source.split("'" + sourceDatabase + "'").join("'" + validationDatabase + "'");
    eval(rewritten);
  }

  validationDatabases.forEach(function (databaseName) {
    db.getSiblingDB(databaseName).dropDatabase();
  });

  executeSeed('02-account-profiles.js', 'profile-service', validationDatabases[0]);
  executeSeed('03-post-data.js', 'post-service', validationDatabases[1]);
  executeSeed('04-friend-data.js', 'friend-service', validationDatabases[2]);
  executeSeed('05-chat-data.js', 'chat-service', validationDatabases[3]);
  executeSeed('06-file-data.js', 'file-service', validationDatabases[4]);

  var profileValidationDb = db.getSiblingDB(validationDatabases[0]);
  var postValidationDb = db.getSiblingDB(validationDatabases[1]);
  var friendValidationDb = db.getSiblingDB(validationDatabases[2]);
  var chatValidationDb = db.getSiblingDB(validationDatabases[3]);
  var fileValidationDb = db.getSiblingDB(validationDatabases[4]);

  assertSeed(profileValidationDb.user_profile.countDocuments() === 5, 'expected five profiles');
  assertSeed(profileValidationDb.outbox.countDocuments() === 5, 'expected five profile search events');

  var postCounts = postValidationDb.post.aggregate([
    { $group: { _id: '$userId', count: { $sum: 1 }, imageCount: { $sum: { $cond: [{ $eq: ['$postType', 'IMAGE'] }, 1, 0] } } } }
  ]).toArray();
  assertSeed(postValidationDb.post.countDocuments() === 17, 'expected seventeen posts');
  assertSeed(postCounts.length === 5, 'expected posts for five users');
  postCounts.forEach(function (count) {
    assertSeed(count.count >= 2 && count.count <= 5, 'post count must be in [2,5] for ' + count._id);
    assertSeed(count.imageCount >= 1, 'each user needs an IMAGE post: ' + count._id);
  });
  assertSeed(postValidationDb.post.countDocuments({ postType: 'IMAGE', imageFileIds: { $ne: [] } }) === 0, 'seed image lists must be empty');
  assertSeed(postValidationDb.outbox.countDocuments() === 17, 'expected seventeen post sync events');

  var relationships = friendValidationDb.user_relationship.find().toArray();
  var degrees = {};
  relationships.forEach(function (relationship) {
    degrees[relationship.senderId] = (degrees[relationship.senderId] || 0) + 1;
    degrees[relationship.receiverId] = (degrees[relationship.receiverId] || 0) + 1;
  });
  assertSeed(relationships.length === 7, 'expected seven friendships');
  assertSeed(Object.keys(degrees).length === 5, 'expected five users in friend graph');
  Object.keys(degrees).forEach(function (userId) {
    assertSeed(degrees[userId] >= 2 && degrees[userId] <= 3, 'friend count must be in [2,3] for ' + userId);
  });
  assertSeed(friendValidationDb.outbox.countDocuments() === 14, 'expected bidirectional friend sync events');

  assertSeed(chatValidationDb.conversation.countDocuments() === 7, 'expected seven conversations');
  assertSeed(chatValidationDb.conversation_member.countDocuments() === 14, 'expected fourteen conversation members');
  assertSeed(chatValidationDb.getCollection('chat-message').countDocuments() === 21, 'expected twenty-one messages');
  assertSeed(chatValidationDb.getCollection('chat-message').countDocuments({ $or: [{ content: null }, { content: '' }] }) === 0, 'chat messages must have content');
  assertSeed(chatValidationDb.outbox.countDocuments() === 7, 'expected seven conversation sync events');

  assertSeed(fileValidationDb.file_mgmt.countDocuments() === 110, 'expected exactly the 110 exported file records');

  var summary = {
    profiles: profileValidationDb.user_profile.countDocuments(),
    posts: postValidationDb.post.countDocuments(),
    friendships: relationships.length,
    friendDegrees: degrees,
    conversations: chatValidationDb.conversation.countDocuments(),
    messages: chatValidationDb.getCollection('chat-message').countDocuments(),
    fileMetadata: fileValidationDb.file_mgmt.countDocuments()
  };
  printjson(summary);

  validationDatabases.forEach(function (databaseName) {
    db.getSiblingDB(databaseName).dropDatabase();
  });
})();
