package com.MyProject.file.file_service.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompletedPartRequest {
    Integer partNumber;

    // Lombok sinh getETag()/setETag() từ field "eTag", nhưng Jackson mangle tên bean theo quy tắc
    // JavaBean: khi 2 ký tự đầu sau "get"/"set" đều viết hoa ("ET" trong ETag) thì KHÔNG decapitalize,
    // nên Jackson tự suy ra property JSON là "ETag" (hoa) chứ không phải "eTag" (thường) mà frontend
    // gửi lên -> field luôn null dù client gửi giá trị đúng. Ép property name để khớp JSON từ client.
    @JsonProperty("eTag")
    String eTag;
}
