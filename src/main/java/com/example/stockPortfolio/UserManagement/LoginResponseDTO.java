package com.example.stockPortfolio.UserManagement;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponseDTO {
    private String token;
    private UserResponseDTO user;
}
