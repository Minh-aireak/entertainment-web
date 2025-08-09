package com.MyProject.profile.profile_service.repository;

import com.MyProject.profile.profile_service.entity.UserProfile;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends Neo4jRepository<UserProfile, String> {
    UserProfile findByUsername(String username);
}
