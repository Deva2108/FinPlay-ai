package com.example.stockPortfolio.UserManagement;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProfileUpdateRequestDTO {
    @NotBlank(message = "Name is required")
    private String name;
}
