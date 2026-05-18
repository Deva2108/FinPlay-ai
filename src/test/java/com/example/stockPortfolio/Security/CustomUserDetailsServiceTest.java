package com.example.stockPortfolio.Security;

import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepo userRepo;
    @InjectMocks private CustomUserDetailsService svc;

    private User user(String email, boolean enabled) {
        User u = new User();
        u.setEmail(email);
        u.setPassword("$2a$10$something");
        u.setEnabled(enabled);
        return u;
    }

    @Test
    @DisplayName("Email in ADMIN_EMAILS gets ROLE_ADMIN + ROLE_USER")
    void grantsAdmin() {
        ReflectionTestUtils.setField(svc, "adminEmailsCsv", "boss@finplay.test, devanshdubey2108@gmail.com");
        when(userRepo.findByEmail("boss@finplay.test")).thenReturn(Optional.of(user("boss@finplay.test", true)));

        UserDetails ud = svc.loadUserByUsername("boss@finplay.test");

        assertThat(ud.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(ud.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Email NOT in ADMIN_EMAILS gets only ROLE_USER")
    void noAdmin() {
        ReflectionTestUtils.setField(svc, "adminEmailsCsv", "boss@finplay.test");
        when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(user("alice@example.com", true)));

        UserDetails ud = svc.loadUserByUsername("alice@example.com");

        assertThat(ud.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Empty ADMIN_EMAILS → no admins")
    void emptyAllowlist() {
        ReflectionTestUtils.setField(svc, "adminEmailsCsv", "");
        when(userRepo.findByEmail("anyone@x.com")).thenReturn(Optional.of(user("anyone@x.com", true)));

        UserDetails ud = svc.loadUserByUsername("anyone@x.com");

        assertThat(ud.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Disabled user has isEnabled() == false")
    void disabledUser() {
        ReflectionTestUtils.setField(svc, "adminEmailsCsv", "");
        when(userRepo.findByEmail("ban@x.com")).thenReturn(Optional.of(user("ban@x.com", false)));

        UserDetails ud = svc.loadUserByUsername("ban@x.com");

        assertThat(ud.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Email match is case-insensitive on the allowlist")
    void caseInsensitive() {
        ReflectionTestUtils.setField(svc, "adminEmailsCsv", "Boss@Finplay.test");
        when(userRepo.findByEmail("boss@finplay.test")).thenReturn(Optional.of(user("boss@finplay.test", true)));

        UserDetails ud = svc.loadUserByUsername("BOSS@finplay.TEST");
        assertThat(ud.getAuthorities()).extracting(a -> a.getAuthority()).contains("ROLE_ADMIN");
    }
}
