package com.example.stockPortfolio.AiManagement;

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
public class InsightStatusDTO {
    /** PENDING | READY | ERROR */
    private String status;
    private String topicId;
    private RichInsightDTO insight;   // present only when status == READY
    private String message;
}
