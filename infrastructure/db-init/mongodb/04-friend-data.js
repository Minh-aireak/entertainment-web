// Seven undirected friendships. The resulting degree per account is 3, 3, 3,
// 3 and 2 respectively, satisfying the requested range of two to three.
(function seedFriendData() {
  var friendDb = db.getSiblingDB('friend-service');
  var relationshipClass = 'com.MyProject.friend.friend_service.entity.UserRelationship';
  var outboxClass = 'com.MyProject.friend.friend_service.entity.Outbox';
  var displayNames = {
    '10000000-0000-0000-0000-000000000001': 'Nguyễn Minh An',
    '10000000-0000-0000-0000-000000000002': 'Trần Thu Lan',
    '10000000-0000-0000-0000-000000000003': 'Lê Quang Huy',
    '10000000-0000-0000-0000-000000000004': 'Phạm Ngọc Mai',
    '10000000-0000-0000-0000-000000000005': 'Đỗ Văn Phúc'
  };
  var edges = [
    ['10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002'],
    ['10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000003'],
    ['10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004'],
    ['10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004'],
    ['10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000005'],
    ['10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004'],
    ['10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000005']
  ];

  edges.forEach(function (edge, index) {
    var sorted = edge.slice().sort();
    var shortPair = sorted[0].slice(-1) + '-' + sorted[1].slice(-1);
    var relationshipId = 'seed-friend-' + shortPair;
    var acceptedAt = new Date('2026-08-' + String(10 + index).padStart(2, '0') + 'T03:00:00.000Z');
    var hashFriend = sorted.join('_');

    friendDb.user_relationship.replaceOne(
      { _id: relationshipId },
      {
        _id: relationshipId,
        senderId: sorted[0],
        receiverId: sorted[1],
        hashFriend: hashFriend,
        relationshipStatus: 'FRIEND',
        acceptAt: acceptedAt,
        _class: relationshipClass
      },
      { upsert: true }
    );

    [[sorted[0], sorted[1]], [sorted[1], sorted[0]]].forEach(function (direction) {
      var syncId = 'seed-friend-sync-' + direction[0].slice(-1) + '-' + direction[1].slice(-1);
      friendDb.outbox.updateOne(
        { _id: syncId },
        {
          $setOnInsert: {
            _id: syncId,
            aggregateId: syncId,
            topic: 'friend.sync',
            payload: JSON.stringify({
              id: syncId,
              userId: direction[0],
              friendId: direction[1],
              friendDisplayName: displayNames[direction[1]]
            }),
            createdDate: acceptedAt,
            _class: outboxClass
          }
        },
        { upsert: true }
      );
    });
  });

  print('Seeded friendships: ' + edges.length);
})();
