package com.MyProject.identity.identity_service.repository;

import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=ROLE",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Test
    void findAllWithoutRole_excludesEveryAdminAndKeepsPaginationTotalsCorrect() {
        Role userRole = roleRepository.save(Role.builder().name("USER").build());
        Role adminRole = roleRepository.save(Role.builder().name("ADMIN").build());

        userRepository.save(User.builder()
                .username("regular-1")
                .roles(Set.of(userRole))
                .build());
        userRepository.save(User.builder()
                .username("regular-2")
                .roles(Set.of(userRole))
                .build());
        userRepository.save(User.builder()
                .username("admin-only")
                .roles(Set.of(adminRole))
                .build());
        userRepository.save(User.builder()
                .username("admin-and-user")
                .roles(Set.of(adminRole, userRole))
                .build());

        Page<User> firstPage = userRepository.findAllWithoutRole(
                "ADMIN",
                PageRequest.of(0, 1, Sort.by("username"))
        );

        assertThat(firstPage.getContent())
                .extracting(User::getUsername)
                .containsExactly("regular-1");
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }
}
