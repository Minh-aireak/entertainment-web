package com.MyProject.identity.identity_service.configuration;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.MyProject.identity.identity_service.dto.event.UserRegisteredEvent;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;
import com.MyProject.identity.identity_service.service.OutboxEventPublisher;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ApplicationInitConfig {
    PasswordEncoder passwordEncoder;
    RoleRepository roleRepository;
    OutboxEventPublisher outboxEventPublisher;

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
                        .email("admin@aireak.local")
                        .password(passwordEncoder.encode(System.getenv("BOOTSTRAP_ADMIN_PASSWORD")))
                        .roles(roles)
                        .build();
                user = userRepository.save(user);

                // Other services (profile-service, ...) bootstrap their own state off this
                // event when a user registers normally; the seeded admin bypasses that
                // registration endpoint, so it must be published here too or admin ends up
                // with no profile and every profile-dependent call (e.g. login) 404s.
                UserRegisteredEvent userRegisteredEvent = UserRegisteredEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .userId(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .displayName(user.getUsername())
                        .joinDate(LocalDateTime.now())
                        .build();
                outboxEventPublisher.publish(user.getId(), "user.registered", userRegisteredEvent);
            }
            if (roleRepository.findById("USER").isEmpty()) {
                roleRepository.save(
                        Role.builder().name("USER").description("User role").build());
            }
        };
    }
}
