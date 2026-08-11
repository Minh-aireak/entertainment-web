// Profiles for the five deterministic USER accounts in identity-service/data.sql.
// Wrapped in an IIFE because mongosh load() shares one global scope across files.
(function seedAccountProfiles() {
  var profileDb = db.getSiblingDB('profile-service');
  var joinedAt = new Date('2026-08-10T01:00:00.000Z');
  var profiles = [
    {
      _id: '10000000-0000-0000-0000-000000000001',
      username: 'nguyenminhan',
      email: 'minhan@example.test',
      displayName: 'Nguyễn Minh An',
      firstName: 'Minh An',
      lastName: 'Nguyễn',
      city: 'Hà Nội'
    },
    {
      _id: '10000000-0000-0000-0000-000000000002',
      username: 'tranthulan',
      email: 'thulan@example.test',
      displayName: 'Trần Thu Lan',
      firstName: 'Thu Lan',
      lastName: 'Trần',
      city: 'Đà Nẵng'
    },
    {
      _id: '10000000-0000-0000-0000-000000000003',
      username: 'lequanghuy',
      email: 'quanghuy@example.test',
      displayName: 'Lê Quang Huy',
      firstName: 'Quang Huy',
      lastName: 'Lê',
      city: 'Thành phố Hồ Chí Minh'
    },
    {
      _id: '10000000-0000-0000-0000-000000000004',
      username: 'phamngocmai',
      email: 'ngocmai@example.test',
      displayName: 'Phạm Ngọc Mai',
      firstName: 'Ngọc Mai',
      lastName: 'Phạm',
      city: 'Huế'
    },
    {
      _id: '10000000-0000-0000-0000-000000000005',
      username: 'dovanphuc',
      email: 'vanphuc@example.test',
      displayName: 'Đỗ Văn Phúc',
      firstName: 'Văn Phúc',
      lastName: 'Đỗ',
      city: 'Hải Phòng'
    }
  ];

  profiles.forEach(function (profile) {
    var storedProfile = Object.assign({}, profile, {
      joinDate: joinedAt,
      _class: 'com.MyProject.profile.profile_service.entity.UserProfile'
    });
    profileDb.user_profile.replaceOne({ _id: profile._id }, storedProfile, { upsert: true });

    var eventId = 'seed-profile-search-' + profile._id;
    profileDb.outbox.updateOne(
      { _id: eventId },
      {
        $setOnInsert: {
          _id: eventId,
          aggregateId: profile._id,
          topic: 'search.sync',
          payload: JSON.stringify({
            eventId: eventId,
            userId: profile._id,
            username: profile.username,
            displayName: profile.displayName,
            avatarFileId: null,
            version: '1.0'
          }),
          createdDate: joinedAt,
          processed: false,
          _class: 'com.MyProject.profile.profile_service.entity.Outbox'
        }
      },
      { upsert: true }
    );
  });

  print('Seeded account profiles: ' + profiles.length);
})();
