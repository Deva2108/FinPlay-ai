package com.example.stockPortfolio.AiManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import com.example.stockPortfolio.AiManagement.service.GeminiService;
import com.example.stockPortfolio.UserManagement.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tutorial")
@RequiredArgsConstructor
public class TutorialController {

    private final GeminiService geminiService;
    private final UserService userService;

    @GetMapping("/insight")
    public ResponseEntity<ApiResponse<TutorialInsightResponseDTO>> getTutorialInsight(
            @RequestParam(defaultValue = "investing") String topic,
            @RequestParam(required = false) String context) {
        
        String userContext = context;
        try {
            // Check if user is logged in to add light personalization
            String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            if (email != null && !email.equals("anonymousUser")) {
                com.example.stockPortfolio.UserManagement.User user = userService.getUserByEmail(email);
                userContext = "User: " + user.getName() + ", interested in " + topic;
            }
        } catch (Exception e) {
            // Light personalization fails silently
        }

        String message = geminiService.getTutorialInsight(topic, userContext);
        
        TutorialInsightResponseDTO data = TutorialInsightResponseDTO.builder().message(message).build();
        
        return ResponseEntity.ok(ApiResponse.ok(data, "Tutorial insight generated"));
    }
}
