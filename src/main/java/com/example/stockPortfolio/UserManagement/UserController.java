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

    @PostMapping("/api/auth/register")
    public ResponseEntity<ApiResponse<UserResponseDTO>> register(@Valid @RequestBody RegistrationRequestDTO registrationRequest){
        UserResponseDTO saved = userService.register(registrationRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(saved, "User registered successfully"));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(@Valid @RequestBody LoginRequestDTO loginRequest){
        String token = userService.login(loginRequest.getEmail(), loginRequest.getPassword());
        User user = userService.getUserByEmail(loginRequest.getEmail());
        
        LoginResponseDTO data = LoginResponseDTO.builder()
                .token(token)
                .user(UserResponseDTO.fromEntity(user))
                .build();
        
        return ResponseEntity.ok(ApiResponse.ok(data, "Login successful"));
    }

    @PutMapping("/api/user/profile/{email}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> update(@PathVariable String email, @Valid @RequestBody ProfileUpdateRequestDTO updateRequest){
        UserResponseDTO updatedUser = userService.editProfile(email, updateRequest);
        return ResponseEntity.ok(ApiResponse.ok(updatedUser, "Profile updated successfully"));
    }
}
