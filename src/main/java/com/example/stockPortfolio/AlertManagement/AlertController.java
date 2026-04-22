package com.example.stockPortfolio.AlertManagement;

import com.example.stockPortfolio.UserManagement.UserModel;
import com.example.stockPortfolio.UserManagement.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alerts")
@Tag(name="4. Alert", description = "4th Controller, Alerts")
@Slf4j
public class AlertController {

    @Autowired
    public AlertService alertService;

    @Autowired
    private AlertRepo alertRepo;

    @Autowired
    private AlertHistoryRepo historyRepo;

    @Autowired
    private UserService userService;

    private Long getLoggedInUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserModel user = userService.getUserByEmail(email);
        return user.getUserId();
    }

    @GetMapping
    public ResponseEntity<List<Alert>> getAlerts(){
        return ResponseEntity.ok(alertRepo.findByUserId(getLoggedInUserId()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<AlertHistory>> getAlertHistory() {
        return ResponseEntity.ok(historyRepo.findByUserIdOrderByTriggeredAtDesc(getLoggedInUserId()));
    }

    @PostMapping
    public ResponseEntity<AlertDTO> addAlert(@Valid @RequestBody Alert alert){
        Long userId = getLoggedInUserId();
        log.info("Creating alert for user {} symbol {}", userId, alert.getSymbol());
        alert.setUserId(userId);
        AlertDTO alertDTO = alertService.addAlert(alert);
        return new ResponseEntity<>(alertDTO, HttpStatus.valueOf(alertDTO.getStatus()));
    }
}
