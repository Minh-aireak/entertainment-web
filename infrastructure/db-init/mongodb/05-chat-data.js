// A direct conversation with meaningful messages is created for every seeded
// friendship. IDs and participant hashes match the chat-service conventions.
(function seedChatData() {
  var chatDb = db.getSiblingDB('chat-service');
  var names = {
    '10000000-0000-0000-0000-000000000001': 'Nguyễn Minh An',
    '10000000-0000-0000-0000-000000000002': 'Trần Thu Lan',
    '10000000-0000-0000-0000-000000000003': 'Lê Quang Huy',
    '10000000-0000-0000-0000-000000000004': 'Phạm Ngọc Mai',
    '10000000-0000-0000-0000-000000000005': 'Đỗ Văn Phúc'
  };
  var conversations = [
    {
      users: ['10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002'],
      messages: ['Lan ơi, cuối tuần cậu có rảnh xem phim cùng nhóm không?', 'Mình rảnh tối thứ bảy, An gửi tên phim để mình xem trước nhé.', 'Vậy mình đặt phòng lúc tám giờ, gần đến giờ mình sẽ nhắn lại.']
    },
    {
      users: ['10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000003'],
      messages: ['Huy đã xem tập mới của bộ phim hôm trước chưa?', 'Mình vừa xem xong, đoạn cuối mở ra khá nhiều câu hỏi.', 'Để tối nay mình xem rồi hai đứa bàn tiếp, nhớ đừng tiết lộ nhé.']
    },
    {
      users: ['10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004'],
      messages: ['Mai có muốn tham gia buổi chạy bộ sáng chủ nhật không?', 'Có chứ, nhưng mình chỉ chạy nhẹ khoảng ba cây số thôi.', 'Không sao, mục tiêu là vận động vui vẻ; bảy giờ gặp ở cổng công viên nhé.']
    },
    {
      users: ['10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004'],
      messages: ['Mai ơi, công thức bánh hôm trước cậu gửi rất dễ làm.', 'Thật tốt quá, lần sau mình gửi thêm cách chỉnh nhiệt cho lò nhỏ.', 'Cảm ơn Mai, cuối tuần mình thử lại rồi gửi ảnh thành phẩm.']
    },
    {
      users: ['10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000005'],
      messages: ['Phúc còn giữ danh sách nhạc đi đường dài lần trước không?', 'Mình còn, tối nay mình sắp xếp lại rồi gửi Lan.', 'Tuyệt quá, tuần sau mình đi tàu nên đang cần một danh sách thật nhẹ nhàng.']
    },
    {
      users: ['10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004'],
      messages: ['Mai đã đọc xong cuốn sách cậu đăng hôm qua chưa?', 'Mình mới đọc được hai phần ba nhưng càng đọc càng cuốn.', 'Khi nào đọc xong cho mình mượn nhé, mình cũng thích cách kể chuyện đó.']
    },
    {
      users: ['10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000005'],
      messages: ['Phúc có biết quán cà phê yên tĩnh nào gần bến cảng không?', 'Có một quán trên đường Lê Thánh Tông, buổi sáng khá vắng và nhìn ra sông.', 'Nghe hợp quá, mai mình qua đó làm việc; cảm ơn Phúc nhé.']
    }
  ];
  var conversationClass = 'direct';
  var memberClass = 'com.MyProject.chat_service.entity.ConversationMember';
  var messageClass = 'com.MyProject.chat_service.entity.ChatMessage';
  var outboxClass = 'com.MyProject.chat_service.entity.Outbox';

  conversations.forEach(function (seed, conversationIndex) {
    var users = seed.users.slice().sort();
    var pair = users[0].slice(-1) + '-' + users[1].slice(-1);
    var conversationId = 'seed-conversation-' + pair;
    var participantsHash = users.join('_');
    var baseTime = new Date(Date.UTC(2026, 7, 10 + conversationIndex, 8, 0, 0));
    var lastMessageId = 'seed-message-' + pair + '-03';
    var lastMessageTime = new Date(baseTime.getTime() + 2 * 60000);

    chatDb.conversation.replaceOne(
      { _id: conversationId },
      {
        _id: conversationId,
        participantsHash: participantsHash,
        type: 'DIRECT',
        createdDate: baseTime,
        modifiedDate: lastMessageTime,
        totalSeq: NumberLong('3'),
        userIds: users,
        lastMessage: seed.messages[2],
        deleted: false,
        _class: conversationClass
      },
      { upsert: true }
    );

    users.forEach(function (userId) {
      var memberId = 'seed-member-' + pair + '-' + userId.slice(-1);
      chatDb.conversation_member.replaceOne(
        { _id: memberId },
        {
          _id: memberId,
          conversationId: conversationId,
          userId: userId,
          lastSeenMessageId: lastMessageId,
          lastSeenSeq: NumberLong('3'),
          lastSeenAt: lastMessageTime,
          _class: memberClass
        },
        { upsert: true }
      );
    });

    seed.messages.forEach(function (content, messageIndex) {
      var sequence = messageIndex + 1;
      var messageId = 'seed-message-' + pair + '-0' + sequence;
      var messageTime = new Date(baseTime.getTime() + messageIndex * 60000);
      var senderId = users[messageIndex % 2];
      chatDb.getCollection('chat-message').replaceOne(
        { _id: messageId },
        {
          _id: messageId,
          conversationId: conversationId,
          senderId: senderId,
          messageType: 'TEXT',
          content: content,
          seq: NumberLong(String(sequence)),
          clientMessageId: 'seed-client-' + pair + '-0' + sequence,
          createdDate: messageTime,
          modifiedDate: messageTime,
          messageStatus: 'SEEN',
          _class: messageClass
        },
        { upsert: true }
      );
    });

    var syncId = 'seed-conversation-sync-' + pair;
    chatDb.outbox.updateOne(
      { _id: syncId },
      {
        $setOnInsert: {
          _id: syncId,
          aggregateId: conversationId,
          topic: 'conversation.sync',
          payload: JSON.stringify({
            id: conversationId,
            type: 'DIRECT',
            conversationName: names[users[0]] + ', ' + names[users[1]],
            conversationAvatarFileId: null,
            userIds: users,
            totalSeq: 3,
            createdDate: baseTime.toISOString(),
            modifiedDate: lastMessageTime.toISOString(),
            participantsHash: participantsHash,
            lastMessage: seed.messages[2],
            deleted: false
          }),
          createdDate: lastMessageTime,
          _class: outboxClass
        }
      },
      { upsert: true }
    );
  });

  print('Seeded conversations: ' + conversations.length + ', messages: ' + (conversations.length * 3));
})();
