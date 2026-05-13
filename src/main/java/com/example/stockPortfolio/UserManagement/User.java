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
            regexp = "[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
            message = "Please enter a valid email address, like example@domain.com")
    private String email;

    @NotBlank(message = "Password required!")
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * Soft-disable flag. When false the user cannot authenticate.
     * Existing valid JWTs continue to work until they expire (≤24h) — acceptable
     * tradeoff vs adding a server-side token store.
     *
     * Postgres + H2 both honour the {@code columnDefinition} default so that
     * adding this column to an existing table (ddl-auto=update) backfills
     * existing rows to TRUE. New rows default TRUE via the field initializer.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean enabled = true;

    /** Updated on every successful login. Useful in the admin user list. */
    private java.time.LocalDateTime lastLoginAt;

    /** Total experience points earned through education and trading. */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer experiencePoints = 0;
}
