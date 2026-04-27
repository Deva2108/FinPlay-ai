package com.example.stockPortfolio.AiManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/explain")
@lombok.RequiredArgsConstructor
public class ExplainController {

    private final com.example.stockPortfolio.AiManagement.service.GeminiService geminiService;

    @PostMapping
    public ResponseEntity<ApiResponse<ExplainResponseDTO>> getExplanation(@RequestBody ExplainRequestDTO request) {
        ExplainResponseDTO response = geminiService.getStructuredExplanation(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "AI explanation generated"));
    }
}
