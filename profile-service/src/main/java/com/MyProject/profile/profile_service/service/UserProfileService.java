package com.MyProject.profile.profile_service.service;

import com.MyProject.common_dto.event.dto.request.BulkUserProfileRequest;
import com.MyProject.common_dto.event.dto.response.PageResponse;
import com.MyProject.common_dto.event.dto.ProfileUpdatedEvent;
import com.MyProject.common_dto.event.dto.response.UserProfileResponse;
import com.MyProject.profile.profile_service.dto.request.SearchUserProfileRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
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
    KafkaTemplate<String, Object> kafkaTemplate;

    private String getUserName(){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return authentication.getName();
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse createProfile(UserProfileCreationRequest request){
        UserProfile userProfile = userProfileMapper.toUserProfile(request);
        return userProfileMapper.toUserProfileResponse(userProfileRepository.save(userProfile));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateProfile(UserProfileUpdateRequest request){
        String userName = getUserName();
        UserProfile profile = userProfileRepository.findByUsername(userName);

        userProfileMapper.update(profile, request);

        UserProfile savedProfile = userProfileRepository.save(profile);

        ProfileUpdatedEvent event = ProfileUpdatedEvent.builder()
                .userId(savedProfile.getUserId())
                .avatar(savedProfile.getAvatar())
                .displayName(savedProfile.getDisplayName())
                .build();

        kafkaTemplate.send("profile-updated", event);

        return userProfileMapper.toUserProfileResponse(savedProfile);
    }

    @Transactional
    public UserProfileResponse getMyInfo(){
        String userName = getUserName();
        UserProfile profile = userProfileRepository.findByUsername(userName);

        return userProfileMapper.toUserProfileResponse(profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<UserProfileResponse> getAllProfiles(){
        return userProfileRepository.findAll()
                .stream().map(userProfileMapper::toUserProfileResponse)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse getProfile(String userId){
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        return userProfileMapper.toUserProfileResponse(userProfile);
    }

    @Transactional
    public List<UserProfileResponse> getBulkProfiles(BulkUserProfileRequest request){
        List<UserProfile> userProfiles = userProfileRepository.findAllById(request.getUserIds());
        
        return userProfiles.stream()
                .map(userProfileMapper::toUserProfileResponse)
                .toList();
    }

    @Transactional
    public UserProfileResponse updateAvatar(MultipartFile multipartFile){
        String userName = getUserName();
        UserProfile profile = userProfileRepository.findByUsername(userName);

        var response = client.uploadAvatar(multipartFile).getResult();
        String newAvatar = response.getUrl();
        profile.setAvatar(newAvatar);
        
        UserProfile savedProfile = userProfileRepository.save(profile);

        ProfileUpdatedEvent event = ProfileUpdatedEvent.builder()
                .userId(savedProfile.getUserId())
                .avatar(savedProfile.getAvatar())
                .displayName(savedProfile.getDisplayName())
                .build();

        kafkaTemplate.send("profile-updated", event);

        return userProfileMapper.toUserProfileResponse(savedProfile);
    }

    @Transactional
    public PageResponse<UserProfileResponse> searchProfile(SearchUserProfileRequest request){
        Sort sort = Sort.by("joinDate").ascending();
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize(), sort);

        Page<UserProfile> pageData = userProfileRepository.findAllByDisplayName(request.getDisplayName(), pageable);
        List<UserProfileResponse> userProfileResponses = pageData.getContent()
                .stream().map(userProfileMapper::toUserProfileResponse)
                .toList();

        return PageResponse.<UserProfileResponse>builder()
                .currentPage(request.getPage())
                .pageSize(request.getSize())
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(userProfileResponses)
                .build();
    }
}
