package com.example.stockPortfolio.UserManagement;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequestDTO {
    @NotBlank(message = "Email is required!")
    @Email(message = "Please enter a valid email address")
    private String email;
    @NotBlank(message = "Password required!")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
