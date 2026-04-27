package com.example.stockPortfolio.DecisionManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/decision")
@lombok.RequiredArgsConstructor
public class DecisionController {

    private final DecisionService decisionService;
    private final com.example.stockPortfolio.AiManagement.service.GeminiService geminiService;

    @PostMapping
    public ResponseEntity<ApiResponse<DecisionDTO>> saveDecision(@RequestBody DecisionDTO request) {
        DecisionDTO saved = decisionService.saveDecision(request);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Decision recorded successfully"));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse<Map<String, String>>> evaluateDecision(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.ok(decisionService.evaluateDecision(request), "Decision evaluated successfully"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(decisionService.getStats(), "Decision stats fetched successfully"));
    }

    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<Map<String, String>>> getUserInsights() {
        return ResponseEntity.ok(ApiResponse.ok(decisionService.getInsights(), "User insights fetched successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DecisionDTO>>> getAllDecisions() {
        return ResponseEntity.ok(ApiResponse.ok(decisionService.getAllDecisions(), "All decisions fetched successfully"));
    }

    @GetMapping("/archetype")
    public ResponseEntity<ApiResponse<ArchetypeResponseDTO>> getArchetype() {
        List<DecisionDTO> decisions = decisionService.getRecentDecisions();
        
        List<Map<String, Object>> rawDecisions = decisions.stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            map.put("symbol", d.getSymbol());
            map.put("action", d.getAction());
            map.put("price", d.getPrice());
            return map;
        }).collect(Collectors.toList());

        ArchetypeResponseDTO archetype = geminiService.getBehavioralIdentity(rawDecisions);
        return ResponseEntity.ok(ApiResponse.ok(archetype, "Behavioral archetype generated"));
    }
}
