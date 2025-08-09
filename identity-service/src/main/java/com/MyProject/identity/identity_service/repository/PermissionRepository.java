package com.MyProject.identity.identity_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.MyProject.identity.identity_service.entity.Permission;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {}
