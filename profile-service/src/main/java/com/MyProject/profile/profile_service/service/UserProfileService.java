package com.MyProject.profile.profile_service.service;

import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
import com.MyProject.profile.profile_service.dto.response.UserProfileResponse;
import com.MyProject.profile.profile_service.entity.UserProfile;
import com.MyProject.profile.profile_service.exception.AppException;
import com.MyProject.profile.profile_service.exception.ErrorCode;
import com.MyProject.profile.profile_service.mapper.UserProfileMapper;
import com.MyProject.profile.profile_service.repository.UserProfileRepository;
import com.MyProject.profile.profile_service.repository.httpclient.FileClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileService {
    UserProfileRepository userProfileRepository;
    UserProfileMapper userProfileMapper;
    FileClient client;

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse createProfile(UserProfileCreationRequest request){
        UserProfile userProfile = userProfileMapper.toUserProfile(request);
        return userProfileMapper.toUserProfileResponse(userProfileRepository.save(userProfile));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateProfile(UserProfileUpdateRequest request){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        UserProfile profile = userProfileRepository.findByUsername(authentication.getName());

        userProfileMapper.update(profile, request);

        return userProfileMapper.toUserProfileResponse(userProfileRepository.save(profile));
    }

    @Transactional
    public UserProfileResponse getMyInfo(){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        UserProfile profile = userProfileRepository.findByUsername(authentication.getName());

        return userProfileMapper.toUserProfileResponse(profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<UserProfileResponse> getAllProfiles(){
        return userProfileMapper.toListUserProfileResponse(userProfileRepository.findAll());
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse getProfile(String id){
        UserProfile userProfile = userProfileRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        return userProfileMapper.toUserProfileResponse(userProfile);
    }

    @Transactional
    public UserProfileResponse updateAvatar(MultipartFile multipartFile){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        UserProfile profile = userProfileRepository.findByUsername(authentication.getName());

        var response = client.uploadAvatar(multipartFile);
        profile.setAvatar(response.getResult().getUrl());

        return userProfileMapper.toUserProfileResponse(userProfileRepository.save(profile));
    }
}
