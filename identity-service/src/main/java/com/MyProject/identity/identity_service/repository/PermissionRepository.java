package com.MyProject.identity.identity_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.MyProject.identity.identity_service.entity.Permission;

public interface PermissionRepository extends JpaRepository<Permission, String> {}
