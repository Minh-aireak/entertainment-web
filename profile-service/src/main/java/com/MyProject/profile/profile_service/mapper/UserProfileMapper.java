package com.MyProject.profile.profile_service.mapper;

import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.profile.profile_service.document.UserProfileDoc;
import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
import com.MyProject.profile.profile_service.entity.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserProfileMapper {
    @Mapping(target = "avatarFileId", ignore = true)
    UserProfile toUserProfile(UserProfileCreationRequest request);

    // avatar (URL) không map tự động từ avatarFileId - phải resolve presigned URL mới qua
    // file-service ở tầng service (xem UserProfileService.resolveAvatarUrl), không lưu tĩnh.
    @Mapping(target = "avatar", ignore = true)
    UserProfileResponse toUserProfileResponse(UserProfile userProfile);

    UserProfileDoc toUserProfileDoc(UserProfile userProfile);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "joinDate", ignore = true)
    void update(@MappingTarget UserProfile userProfile, UserProfileUpdateRequest request);
}
