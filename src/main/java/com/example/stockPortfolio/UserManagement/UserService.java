package com.example.stockPortfolio.UserManagement;

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
    public UserResponseDTO register(RegistrationRequestDTO dto){
        if(userRepo.existsByEmail(dto.getEmail())){
            throw new UserNotFoundException("User already exists in our database");
        }
        
        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        
        User savedUser = userRepo.save(user);
        
        // Auto-create initial portfolio for UX
        Portfolio defaultPortfolio = new Portfolio();
        defaultPortfolio.setPortfolioName("My Learning Portfolio 🎒");
        portfolioService.addPortfolio(defaultPortfolio, savedUser);
        
        return UserResponseDTO.fromEntity(savedUser);
    }

    public String login(String email, String password){
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (Exception e) {
            throw new UserNotFoundException("Incorrect email or password!");
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        return jwtUtils.generateToken(userDetails);
    }

    public User getUserByEmail(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }

    @Transactional
    public UserResponseDTO editProfile(String email, ProfileUpdateRequestDTO dto){
        // Security Check: Ensure the logged-in user is only editing their own profile
        String loggedInEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        if (!loggedInEmail.equalsIgnoreCase(email)) {
            throw new RuntimeException("Unauthorized: You can only edit your own profile.");
        }

        return userRepo.findByEmail(email)
                .map(existingUser -> {
                    if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
                        existingUser.setName(dto.getName().trim());
                    }
                    return UserResponseDTO.fromEntity(userRepo.save(existingUser));
                }).orElseThrow(() -> new UserNotFoundException("Email not found: " + email));
    }
}
