package com.MyProject.identity.identity_service.configuration;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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
    ApplicationRunner applicationRunner(
            UserRepository userRepository,
            @Value("${app.bootstrap-admin.enabled:false}") boolean bootstrapAdminEnabled,
            @Value("${app.bootstrap-admin.username:}") String bootstrapAdminUsername,
            @Value("${app.bootstrap-admin.email:}") String bootstrapAdminEmail,
            @Value("${app.bootstrap-admin.password:}") String bootstrapAdminPassword) {
        return args -> {
            if (bootstrapAdminEnabled) {
                if (bootstrapAdminUsername.isBlank()
                        || bootstrapAdminEmail.isBlank()
                        || bootstrapAdminPassword.isBlank()) {
                    throw new IllegalStateException(
                            "Bootstrap admin is enabled but username, email or password is missing");
                }

                if (userRepository.findByUsername(bootstrapAdminUsername).isEmpty()) {

                    Role role = roleRepository
                            .findById("ADMIN")
                            .orElseGet(() -> roleRepository.save(Role.builder()
                                    .name("ADMIN")
                                    .description("Admin role")
                                    .build()));

                    Set<Role> roles = new HashSet<>();
                    roles.add(role);

                    User user = User.builder()
                            .username(bootstrapAdminUsername)
                            .email(bootstrapAdminEmail)
                            .password(passwordEncoder.encode(bootstrapAdminPassword))
                            .roles(roles)
                            .build();
                    user = userRepository.save(user);

                    // Other services bootstrap their own state from this registration event.
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
            }
            if (roleRepository.findById("USER").isEmpty()) {
                roleRepository.save(
                        Role.builder().name("USER").description("User role").build());
            }
        };
    }
}
