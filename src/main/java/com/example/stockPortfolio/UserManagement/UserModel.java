package com.example.stockPortfolio.UserManagement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//our database model for each user
@Data
@Entity
public class UserModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;
    @NotBlank(message = "Name is required!")
    private String name;
    @NotBlank(message = "Email is required!")
    @Column(unique = true)
    @Pattern(
            regexp = "[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}",
            message = "Please enter a valid email address, like example@domain.com")
    private String email;
    @NotBlank(message = "Password required!")
    private String password;
}
