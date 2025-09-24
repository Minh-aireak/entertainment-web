//package com.MyProject.weather_service.mapper;
//
//import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
//import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
//import com.MyProject.profile.profile_service.dto.response.UserProfileResponse;
//import com.MyProject.profile.profile_service.entity.UserProfile;
//import org.mapstruct.Mapper;
//import org.mapstruct.MappingTarget;
//import org.mapstruct.NullValuePropertyMappingStrategy;
//
//import java.util.List;
//
//@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
//public interface UserProfileMapper {
//    UserProfile toUserProfile(UserProfileCreationRequest request);
//
//    UserProfileResponse toUserProfileResponse(UserProfile userProfile);
//
//    List<UserProfileResponse> toListUserProfileResponse(List<UserProfile> list);
//
//    void update(@MappingTarget UserProfile userProfile, UserProfileUpdateRequest request);
//}
