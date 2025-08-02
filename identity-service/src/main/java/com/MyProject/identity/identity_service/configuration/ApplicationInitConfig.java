package com.MyProject.identity.identity_service.configuration;

import java.util.HashSet;
import java.util.Set;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.repository.PermissionRepository;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ApplicationInitConfig {
    PasswordEncoder passwordEncoder;
    RoleRepository roleRepository;
    PermissionRepository permissionRepository;

    @Bean
    @Profile("!test")
    @Transactional
    ApplicationRunner applicationRunner(UserRepository userRepository) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {

                Role role = roleRepository
                        .findById("ADMIN")
                        .orElseGet(() -> roleRepository.save(Role.builder()
                                .name("ADMIN")
                                .description("Admin role")
                                .build()));

                Set<Role> roles = new HashSet<>();
                roles.add(role);

                User user = User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode(System.getenv("BOOTSTRAP_ADMIN_PASSWORD")))
                        .roles(roles)
                        .build();
                userRepository.save(user);
            }
            if (roleRepository.findById("USER").isEmpty()) {
                roleRepository.save(
                        Role.builder().name("USER").description("User role").build());
            }
        };
    }
}
