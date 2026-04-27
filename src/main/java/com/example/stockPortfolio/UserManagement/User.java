package com.example.stockPortfolio.UserManagement;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//our database model for each user
@Data
@Entity
@Table(name = "users")
public class User {
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
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;
}
