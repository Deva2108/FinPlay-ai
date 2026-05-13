package com.example.stockPortfolio.UserManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name="1. User & Auth", description = "User Authentication and Profile Management")
@lombok.RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final com.example.stockPortfolio.Security.CustomUserDetailsService customUserDetailsService;

    @PostMapping("/api/auth/register")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> register(@Valid @RequestBody RegistrationRequestDTO registrationRequest){
        LoginResponseDTO result = userService.register(registrationRequest);
        // Decorate with admin flag so the freshly-registered user's UI knows whether to show /admin.
        if (result.getUser() != null) {
            result.getUser().setAdmin(customUserDetailsService.isAdminEmail(result.getUser().getEmail()));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result, "User registered successfully"));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(@Valid @RequestBody LoginRequestDTO loginRequest){
        String token = userService.login(loginRequest.getEmail(), loginRequest.getPassword());
        User user = userService.getUserByEmail(loginRequest.getEmail());

        UserResponseDTO userDto = UserResponseDTO.fromEntity(user,
                customUserDetailsService.isAdminEmail(user.getEmail()));

        LoginResponseDTO data = LoginResponseDTO.builder()
                .token(token)
                .user(userDto)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(data, "Login successful"));
    }

    /** Whoami — used by the frontend to refresh role on page reload (since JWTs don't carry it). */
    @GetMapping("/api/auth/me")
    public ResponseEntity<ApiResponse<UserResponseDTO>> me() {
        String email = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        User user = userService.getUserByEmail(email);
        UserResponseDTO dto = UserResponseDTO.fromEntity(user, customUserDetailsService.isAdminEmail(email));
        return ResponseEntity.ok(ApiResponse.ok(dto, "ok"));
    }

    @PutMapping("/api/user/profile/{email}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> update(@PathVariable String email, @Valid @RequestBody ProfileUpdateRequestDTO updateRequest){
        UserResponseDTO updatedUser = userService.editProfile(email, updateRequest);
        return ResponseEntity.ok(ApiResponse.ok(updatedUser, "Profile updated successfully"));
    }
}
