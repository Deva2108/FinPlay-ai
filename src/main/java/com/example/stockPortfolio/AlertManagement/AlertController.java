package com.example.stockPortfolio.AlertManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@Tag(name="4. Alert", description = "Alert Management Controller")
@Slf4j
@lombok.RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final UserService userService;

    private Long getLoggedInUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getUserByEmail(email);
        return user.getUserId();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AlertDataDTO>>> getAlerts(){
        List<AlertDataDTO> alerts = alertService.getAlertsByUserId(getLoggedInUserId());
        return ResponseEntity.ok(ApiResponse.ok(alerts, "Alerts fetched successfully"));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<AlertHistoryResponseDTO>>> getAlertHistory() {
        List<AlertHistoryResponseDTO> history = alertService.getAlertHistoryByUserId(getLoggedInUserId()).stream()
                .map(AlertHistoryResponseDTO::fromEntity)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(history, "Alert history fetched successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AlertDTO>> addAlert(@Valid @RequestBody AlertRequestDTO dto){
        Long userId = getLoggedInUserId();
        log.info("Creating alert for user {} symbol {}", userId, dto.getSymbol());
        AlertDTO alertDTO = alertService.addAlert(dto, userId);
        return ResponseEntity.status(HttpStatus.valueOf(alertDTO.getStatus()))
                .body(ApiResponse.ok(alertDTO, "Alert created successfully"));
    }
}
