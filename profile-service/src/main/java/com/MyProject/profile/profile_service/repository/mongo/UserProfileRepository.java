package com.MyProject.profile.profile_service.repository.mongo;

import com.MyProject.profile.profile_service.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends MongoRepository<UserProfile, String> {
    Page<UserProfile> findAllByDisplayName(String displayName, Pageable pageable);
}
