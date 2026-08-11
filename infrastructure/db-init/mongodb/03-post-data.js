// Meaningful social posts for the five seeded accounts. Every account owns
// between two and five posts and at least one IMAGE post with an empty file list
// ready for the user to attach an image later.
(function seedPostData() {
  var postDb = db.getSiblingDB('post-service');
  var postClass = 'com.MyProject.post.post_service.entity.Post';
  var outboxClass = 'com.MyProject.post.post_service.entity.Outbox';
  var definitions = [
    ['10000000-0000-0000-0000-000000000001', '01', 'IMAGE', 'Một chiều bình yên bên hồ', 'Chiều nay trời dịu, mình đi bộ một vòng quanh hồ và nhận ra đôi khi nghỉ chậm lại cũng là một cách nạp năng lượng.', '2026-08-10T02:00:00'],
    ['10000000-0000-0000-0000-000000000001', '02', 'TEXT', 'Bộ phim khiến mình suy nghĩ', 'Mình vừa xem một câu chuyện về lòng trung thành. Điều đọng lại nhất là cách các nhân vật vẫn giữ lời hứa trong hoàn cảnh khó khăn.', '2026-08-10T04:20:00'],
    ['10000000-0000-0000-0000-000000000001', '03', 'TEXT', 'Kế hoạch cho tuần mới', 'Tuần này mình đặt mục tiêu đọc xong một cuốn sách, tập thể dục ba buổi và dành một tối xem phim cùng bạn bè.', '2026-08-11T01:15:00'],

    ['10000000-0000-0000-0000-000000000002', '01', 'IMAGE', 'Góc bếp cuối tuần', 'Mẻ bánh đầu tiên chưa thật đẹp nhưng mùi bơ thơm khắp nhà. Mình sẽ bổ sung ảnh thành phẩm sau nhé.', '2026-08-10T03:10:00'],
    ['10000000-0000-0000-0000-000000000002', '02', 'TEXT', 'Một lời cảm ơn nhỏ', 'Cảm ơn những người bạn đã lắng nghe mình trong tuần vừa rồi. Một cuộc trò chuyện chân thành có thể làm ngày dài trở nên nhẹ hơn.', '2026-08-10T08:30:00'],
    ['10000000-0000-0000-0000-000000000002', '03', 'TEXT', 'Gợi ý phim tối nay', 'Nếu thích nhịp phim nhanh và nhiều bí ẩn, mọi người có thể chọn một phim hành động rồi cùng đoán nút thắt trước đoạn kết.', '2026-08-11T05:40:00'],
    ['10000000-0000-0000-0000-000000000002', '04', 'TEXT', 'Thói quen mới mỗi sáng', 'Mình đang thử bắt đầu ngày mới bằng mười phút giãn cơ và một cốc nước ấm. Sau một tuần, tinh thần tỉnh táo hơn hẳn.', '2026-08-12T00:25:00'],

    ['10000000-0000-0000-0000-000000000003', '01', 'IMAGE', 'Thành phố sau cơn mưa', 'Đường phố phản chiếu ánh đèn rất đẹp sau cơn mưa tối qua. Mình để sẵn bài và sẽ thêm tấm ảnh ưng ý nhất sau.', '2026-08-10T06:45:00'],
    ['10000000-0000-0000-0000-000000000003', '02', 'TEXT', 'Âm nhạc và những chuyến đi', 'Một danh sách nhạc phù hợp làm quãng đường dài ngắn lại. Mình thường chọn những bài có tiết tấu nhẹ khi đi tàu.', '2026-08-11T09:10:00'],

    ['10000000-0000-0000-0000-000000000004', '01', 'IMAGE', 'Buổi sớm ở Huế', 'Sáng sớm thành phố yên tĩnh, nắng vừa chạm lên mái ngói. Mình sẽ thêm ảnh sau khi chọn xong khung hình đẹp nhất.', '2026-08-10T00:35:00'],
    ['10000000-0000-0000-0000-000000000004', '02', 'TEXT', 'Cuốn sách đang đọc', 'Mình thích cách cuốn sách này kể những điều lớn lao bằng các chi tiết rất đời thường. Đọc chậm mới thấy hết sự tinh tế.', '2026-08-10T07:50:00'],
    ['10000000-0000-0000-0000-000000000004', '03', 'TEXT', 'Hẹn một phòng xem chung', 'Cuối tuần này mình muốn mở phòng xem chung một bộ phim phiêu lưu. Ai tham gia thì nhắn để mình chọn giờ phù hợp nhé.', '2026-08-11T03:05:00'],
    ['10000000-0000-0000-0000-000000000004', '04', 'TEXT', 'Bữa cơm nhà', 'Đi xa mới thấy một bữa cơm giản dị cùng gia đình quý đến mức nào. Hôm nay mình về sớm để phụ mẹ chuẩn bị bữa tối.', '2026-08-11T11:20:00'],
    ['10000000-0000-0000-0000-000000000004', '05', 'TEXT', 'Điều tốt đẹp trong ngày', 'Niềm vui hôm nay là giúp một vị khách tìm lại chiếc ví để quên. Những việc nhỏ vẫn có thể làm cả hai bên ấm lòng.', '2026-08-12T04:00:00'],

    ['10000000-0000-0000-0000-000000000005', '01', 'IMAGE', 'Bình minh ngoài bến cảng', 'Mặt trời lên sau những cần cẩu tạo thành một khung cảnh rất lạ. Mình sẽ cập nhật ảnh vào bài này sau.', '2026-08-10T01:20:00'],
    ['10000000-0000-0000-0000-000000000005', '02', 'TEXT', 'Chạy bộ không cần quá nhanh', 'Mục tiêu của mình không phải phá kỷ lục mà là duy trì đều đặn. Hoàn thành quãng đường với tinh thần thoải mái đã là một chiến thắng.', '2026-08-11T02:40:00'],
    ['10000000-0000-0000-0000-000000000005', '03', 'TEXT', 'Một bộ phim xem lại vẫn hay', 'Có những bộ phim dù biết trước kết thúc vẫn muốn xem lại vì cảm xúc nằm trong từng cuộc trò chuyện, không chỉ ở nút thắt.', '2026-08-12T06:15:00']
  ];

  definitions.forEach(function (definition, ordinal) {
    var userId = definition[0];
    var localId = definition[1];
    var postType = definition[2];
    var title = definition[3];
    var content = definition[4];
    var localDateTime = definition[5];
    var postId = 'seed-post-' + userId.slice(-1) + '-' + localId;
    var createdDate = new Date(localDateTime + '.000Z');
    var post = {
      _id: postId,
      postType: postType,
      userId: userId,
      title: title,
      content: content,
      createdDate: createdDate,
      modifiedDate: createdDate,
      listUsersJoin: [userId],
      likeCount: NumberLong(String(ordinal % 5)),
      watchParticipantCount: 0,
      _class: postClass
    };
    if (postType === 'IMAGE') {
      post.imageFileIds = [];
    }

    postDb.post.replaceOne({ _id: postId }, post, { upsert: true });

    var eventId = 'seed-post-sync-' + postId;
    postDb.outbox.updateOne(
      { _id: eventId },
      {
        $setOnInsert: {
          _id: eventId,
          aggregateId: postId,
          eventId: eventId,
          topic: 'post.sync',
          payload: JSON.stringify({
            eventId: eventId,
            aggregateId: postId,
            payload: {
              id: postId,
              userId: userId,
              title: title,
              content: content,
              postType: postType,
              createdDate: localDateTime
            }
          }),
          createdDate: createdDate,
          _class: outboxClass
        }
      },
      { upsert: true }
    );
  });

  print('Seeded posts: ' + definitions.length);
})();
