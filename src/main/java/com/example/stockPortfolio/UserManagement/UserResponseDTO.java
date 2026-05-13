package com.example.stockPortfolio.UserManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private Long userId;
    private String name;
    private String email;
    /** True if the user's email is in ADMIN_EMAILS. Drives whether the UI shows /admin. */
    private boolean admin;
    private Integer experiencePoints;

    public static UserResponseDTO fromEntity(User user) {
        return UserResponseDTO.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .experiencePoints(user.getExperiencePoints())
                .build();
    }

    public static UserResponseDTO fromEntity(User user, boolean admin) {
        UserResponseDTO dto = fromEntity(user);
        dto.setAdmin(admin);
        return dto;
    }
}
