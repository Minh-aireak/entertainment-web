package com.MyProject.identity.identity_service.repository;

import java.util.Optional;

import com.MyProject.identity.identity_service.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.MyProject.identity.identity_service.entity.User;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByRolesContaining(Role role);

    @Query(value = """
            SELECT u
            FROM User u
            WHERE NOT EXISTS (
                SELECT userWithRole.id
                FROM User userWithRole
                JOIN userWithRole.roles role
                WHERE userWithRole.id = u.id
                  AND role.name = :roleName
            )
            """,
            countQuery = """
            SELECT COUNT(u)
            FROM User u
            WHERE NOT EXISTS (
                SELECT userWithRole.id
                FROM User userWithRole
                JOIN userWithRole.roles role
                WHERE userWithRole.id = u.id
                  AND role.name = :roleName
            )
            """)
    Page<User> findAllWithoutRole(@Param("roleName") String roleName, Pageable pageable);
}
