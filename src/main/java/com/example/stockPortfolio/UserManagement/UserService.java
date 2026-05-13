package com.example.stockPortfolio.UserManagement;

import com.example.stockPortfolio.ExceptionManagement.UserAlreadyExistsException;
import com.example.stockPortfolio.ExceptionManagement.UserNotFoundException;
import com.example.stockPortfolio.PortfolioManagement.Portfolio;
import com.example.stockPortfolio.PortfolioManagement.PortfolioService;
import com.example.stockPortfolio.Security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
public class UserService {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final PortfolioService portfolioService;

    @Transactional
    public LoginResponseDTO register(RegistrationRequestDTO dto){
        String normalizedEmail = dto.getEmail().toLowerCase();
        if(userRepo.existsByEmail(normalizedEmail)){
            throw new UserAlreadyExistsException("User already exists in our database");
        }

        User user = new User();
        user.setName(dto.getName());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        User savedUser = userRepo.save(user);

        // Auto-create initial portfolio for UX
        Portfolio defaultPortfolio = new Portfolio();
        defaultPortfolio.setPortfolioName("My Learning Portfolio 🎒");
        portfolioService.addPortfolio(defaultPortfolio, savedUser);

        String token = login(normalizedEmail, dto.getPassword());
        return LoginResponseDTO.builder()
                .token(token)
                .user(UserResponseDTO.fromEntity(savedUser))
                .build();
    }

    public String login(String email, String password){
        String normalizedEmail = email.toLowerCase();
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(normalizedEmail, password));
        } catch (org.springframework.security.authentication.DisabledException de) {
            throw new UserNotFoundException("Account is disabled. Contact admin.");
        } catch (Exception e) {
            throw new UserNotFoundException("Incorrect email or password!");
        }

        // Best-effort: update lastLoginAt outside the auth path so a DB hiccup
        // here can't fail an otherwise-valid login.
        try {
            userRepo.findByEmail(normalizedEmail).ifPresent(u -> {
                u.setLastLoginAt(java.time.LocalDateTime.now());
                userRepo.save(u);
            });
        } catch (Exception ignored) { /* non-critical */ }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(normalizedEmail);
        return jwtUtils.generateToken(userDetails);
    }

    public User getUserByEmail(String email) {
        return userRepo.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }

    @Transactional
    public UserResponseDTO editProfile(String email, ProfileUpdateRequestDTO dto){
        String normalizedEmail = email.toLowerCase();
        String loggedInEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        if (!loggedInEmail.equalsIgnoreCase(normalizedEmail)) {
            throw new com.example.stockPortfolio.ExceptionManagement.PortfolioAccessDeniedException("Unauthorized: You can only edit your own profile.");
        }

        return userRepo.findByEmail(normalizedEmail)
                .map(existingUser -> {
                    if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
                        existingUser.setName(dto.getName().trim());
                    }
                    return UserResponseDTO.fromEntity(userRepo.save(existingUser));
                }).orElseThrow(() -> new UserNotFoundException("Email not found: " + email));
    }
}
