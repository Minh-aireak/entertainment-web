// OPTIONAL, one-time data repair - chạy thủ công qua mongosh nếu muốn giữ lại ảnh/tệp đính kèm và
// avatar nhóm cũ thay vì để chúng hiển thị trống cho tới khi user gửi/upload lại.
//
// Bối cảnh: ChatMessage.attachmentFileUrl và ConversationGroup.groupAvatar (field cũ) từng lưu
// thẳng presigned GET URL do file-service ký (bucket B2 private) - loại URL này chỉ có hiệu lực
// ~1h (B2_PRESIGNED_URL_TTL) nên ảnh/tệp ngừng hiển thị được sau khi hết hạn. Field mới
// attachmentFileId/groupAvatarFileId chỉ lưu file ID (key trên B2); URL hiển thị được resolve mới
// mỗi lần trả response (xem ChatFileUrlResolver).
//
// Script này parse lại các field URL cũ (nếu đúng định dạng presigned URL trỏ vào bucket B2 hiện
// tại) để lấy ra key gốc, ghi vào field *FileId mới, rồi xoá field cũ. Không bắt buộc chạy - nếu bỏ
// qua, tin nhắn đính kèm cũ đơn giản không hiển thị được nữa (không phải ảnh vỡ, chỉ là mất khỏi
// bubble), và group chỉ cần đổi ảnh đại diện 1 lần là hoạt động bình thường trở lại.
//
// Điều chỉnh B2_ENDPOINT/B2_BUCKET_NAME bên dưới nếu khác với .env hiện tại.

const endpoint = "s3.us-east-005.backblazeb2.com";
const bucket = "entertainment-app";
const prefix = `https://${endpoint}/${bucket}/`;
const escapedPrefix = prefix.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

db = db.getSiblingDB("chat-service");

function extractFileIdPipeline(sourceField, targetField) {
    return [
        {
            $set: {
                [targetField]: {
                    $let: {
                        vars: {
                            afterPrefix: { $arrayElemAt: [{ $split: [`$${sourceField}`, prefix] }, 1] },
                        },
                        in: { $arrayElemAt: [{ $split: ["$$afterPrefix", "?"] }, 0] },
                    },
                },
            },
        },
        { $unset: sourceField },
    ];
}

db["chat-message"].updateMany(
    { attachmentFileUrl: { $regex: new RegExp("^" + escapedPrefix) } },
    extractFileIdPipeline("attachmentFileUrl", "attachmentFileId")
);
db["chat-message"].updateMany(
    { attachmentFileUrl: { $exists: true } },
    { $unset: { attachmentFileUrl: "" } }
);

db.conversation.updateMany(
    { groupAvatar: { $regex: new RegExp("^" + escapedPrefix) } },
    extractFileIdPipeline("groupAvatar", "groupAvatarFileId")
);
db.conversation.updateMany(
    { groupAvatar: { $exists: true } },
    { $unset: { groupAvatar: "" } }
);
