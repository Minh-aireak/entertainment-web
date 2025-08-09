package com.MyProject.identity.identity_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.MyProject.identity.identity_service.entity.Permission;
import com.MyProject.identity.identity_service.entity.Role;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByName(String name);

    List<Role> findAllByPermissionsContaining(Permission permission);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Role r SET r.permissions = (Select p FROM Permission p " +
            "WHERE p.name IN (SELECT p2.name " +
            "FROM r.permissions p2 " +
            "WHERE p2.name <> :permissionId)) " +
            "WHERE r.name IN :listRoles")
    void removePermissionFromRoles(@Param("permissionId") String permissionId,
                                   @Param("listRoles") List<String> listRoles);
}
