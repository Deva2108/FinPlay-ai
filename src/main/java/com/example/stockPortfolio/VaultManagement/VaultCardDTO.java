package com.example.stockPortfolio.VaultManagement;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VaultCardDTO {
    private String id;
    private String title;
    private String subtitle;
    private String teaser;        // 1-line description shown on card
    private String category;      // QUIZ | LESSON | CHALLENGE
    private boolean locked;       // true = "Coming Soon"
    private Integer xp;           // potential XP, null if locked
    private String ctaLabel;      // e.g. "Start", "Coming Soon"
}
