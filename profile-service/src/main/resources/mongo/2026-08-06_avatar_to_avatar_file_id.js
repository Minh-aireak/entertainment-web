// OPTIONAL, one-time data repair - chạy thủ công qua mongosh nếu muốn giữ lại avatar cũ của user
// thay vì để họ tự upload lại.
//
// Bối cảnh: UserProfile.avatar (field cũ) từng lưu thẳng presigned GET URL do file-service ký
// (bucket B2 private) - loại URL này chỉ có hiệu lực ~1h (B2_PRESIGNED_URL_TTL) nên avatar ngừng
// hiển thị được sau khi hết hạn. Field mới avatarFileId chỉ lưu file ID (key trên B2); URL hiển thị
// được resolve mới mỗi lần trả response (xem UserProfileService.resolveAvatarUrl).
//
// Script này parse lại field "avatar" cũ (nếu đúng định dạng presigned URL trỏ vào bucket B2 hiện
// tại) để lấy ra key gốc, ghi vào avatarFileId, rồi xoá field avatar cũ. Không cần chạy script này -
// nếu bỏ qua, user chỉ cần upload lại avatar 1 lần là hoạt động bình thường.
//
// Điều chỉnh B2_ENDPOINT/B2_BUCKET_NAME bên dưới nếu khác với .env hiện tại.

const endpoint = "s3.us-east-005.backblazeb2.com";
const bucket = "entertainment-app";
const prefix = `https://${endpoint}/${bucket}/`;

db = db.getSiblingDB("profile-service");

db.user_profile.updateMany(
    { avatar: { $regex: new RegExp("^" + prefix.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")) } },
    [
        {
            $set: {
                avatarFileId: {
                    $let: {
                        vars: {
                            afterPrefix: { $arrayElemAt: [{ $split: ["$avatar", prefix] }, 1] },
                        },
                        in: { $arrayElemAt: [{ $split: ["$$afterPrefix", "?"] }, 0] },
                    },
                },
            },
        },
        { $unset: "avatar" },
    ]
);

// Các document còn field "avatar" sau khi chạy xong là avatar không theo đúng định dạng presigned
// URL mong đợi (vd. để trống, hoặc URL ngoài) - xoá nốt field cũ cho gọn, không có gì để migrate.
db.user_profile.updateMany({ avatar: { $exists: true } }, { $unset: { avatar: "" } });
