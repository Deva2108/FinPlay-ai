package com.example.stockPortfolio.Security;

import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds Spring's UserDetails from our domain User.
 *
 * Two extras versus the original implementation:
 *  1. Admins are configured via the env var ADMIN_EMAILS (comma-separated).
 *     Anyone in that list logs in with ROLE_ADMIN. To add or remove admins,
 *     edit the env var and redeploy — no DB column required.
 *  2. {@code user.enabled = false} blocks login via Spring's DisabledException
 *     handling, so soft-disable just works.
 */
@Service
@lombok.RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepo userRepo;

    @Value("${app.admin-emails:}")
    private String adminEmailsCsv;

    private volatile Set<String> adminEmails;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String key = email.toLowerCase();
        User user = userRepo.findByEmail(key)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        List<GrantedAuthority> authorities = isAdmin(key)
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }

    private boolean isAdmin(String emailLower) {
        Set<String> set = adminEmails;
        if (set == null) {
            if (adminEmailsCsv == null || adminEmailsCsv.isBlank()) {
                set = Set.of();
            } else {
                set = Arrays.stream(adminEmailsCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toCollection(HashSet::new));
            }
            adminEmails = set;
        }
        return set.contains(emailLower);
    }

    /** Visible to tests + AdminService for cheap "is X an admin?" checks. */
    public boolean isAdminEmail(String email) {
        return email != null && isAdmin(email.toLowerCase());
    }
}
