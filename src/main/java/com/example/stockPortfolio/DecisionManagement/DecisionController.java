package com.example.stockPortfolio.DecisionManagement;

import com.example.stockPortfolio.UserManagement.UserModel;
import com.example.stockPortfolio.UserManagement.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/decision")
public class DecisionController {

    @Autowired
    private DecisionRepo decisionRepo;

    @Autowired
    private UserService userService;

    private Long getLoggedInUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserModel user = userService.getUserByEmail(email);
        return user.getUserId();
    }

    @PostMapping
    public Decision saveDecision(@RequestBody Decision decision) {
        decision.setUserId(getLoggedInUserId());
        if (decision.getTimestamp() == null) {
            decision.setTimestamp(LocalDateTime.now());
        }
        return decisionRepo.save(decision);
    }

    @PostMapping("/evaluate")
    public Map<String, String> evaluateDecision(@RequestBody Map<String, Object> request) {
        String action = (String) request.getOrDefault("action", "WAIT");
        Double price = Double.valueOf(request.getOrDefault("price", 0.0).toString());
        String symbol = (String) request.getOrDefault("symbol", "Stock");
        boolean isPositive = (boolean) request.getOrDefault("isPositive", true);
        String pattern = (String) request.getOrDefault("pattern", "balanced");

        String outcome;
        String aiResponse;

        if (action.equalsIgnoreCase("BUY")) {
            if (isPositive) {
                outcome = "good";
                aiResponse = "That felt good, right? It always does. You caught the momentum, but don't let the dopamine cloud your judgment. Luck is a fickle friend.";
            } else {
                outcome = "risky";
                aiResponse = "Not wrong. Just predictable. You bought the hype, not the trend. That ₹" + price + " could have bought you a lot of coffee while you waited for a better entry.";
            }
        } else {
            if (isPositive) {
                outcome = "missed";
                aiResponse = "You didn't lose money, you just repeated a pattern of hesitation. The market moved, you didn't. Safety is comfortable, but it's also expensive.";
            } else {
                outcome = "neutral";
                aiResponse = "Smart move. You dodged a bullet by doing absolutely nothing. Discipline is boring until you realize how much capital you just saved. Keep that chill energy.";
            }
        }

        Map<String, String> response = new HashMap<>();
        response.put("outcome", outcome);
        response.put("aiMessage", aiResponse);
        response.put("behaviorHighlight", "Pattern: " + pattern.toUpperCase());
        return response;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Long userId = getLoggedInUserId();
        List<Decision> userDecisions = decisionRepo.findByUserId(userId);
        
        long buys = userDecisions.stream().filter(d -> "BUY".equalsIgnoreCase(d.getAction())).count();
        long skips = userDecisions.stream().filter(d -> "SKIP".equalsIgnoreCase(d.getAction())).count();
        int total = userDecisions.size();

        // Calculate Streak (same action in a row)
        int streak = 0;
        String lastAction = null;
        if (!userDecisions.isEmpty()) {
            for (int i = userDecisions.size() - 1; i >= 0; i--) {
                String currentAction = userDecisions.get(i).getAction();
                if (lastAction == null) {
                    lastAction = currentAction;
                    streak = 1;
                } else if (currentAction.equalsIgnoreCase(lastAction)) {
                    streak++;
                } else {
                    break;
                }
            }
        }

        // Determine Dominant Pattern
        String pattern = "adaptive";
        if (total >= 3) {
            double buyRatio = (double) buys / total;
            double skipRatio = (double) skips / total;
            if (buyRatio > 0.7) pattern = "impulsive";
            else if (skipRatio > 0.7) pattern = "hesitant";
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDecisions", total);
        stats.put("investCount", buys);
        stats.put("spendCount", skips);
        stats.put("currentStreak", streak);
        stats.put("lastAction", lastAction);
        stats.put("dominantPattern", pattern);
        
        return stats;
    }

    @GetMapping("/insights")
    public Map<String, String> getUserInsights() {
        Long userId = getLoggedInUserId();
        List<Decision> userDecisions = decisionRepo.findByUserId(userId);
        
        if (userDecisions.isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("behaviorType", "neutral");
            response.put("insightMessage", "Start making decisions in the Arena to unlock behavioral insights.");
            return response;
        }

        long total = userDecisions.size();
        long buys = userDecisions.stream().filter(d -> "BUY".equalsIgnoreCase(d.getAction())).count();
        double buyRatio = (double) buys / total;

        String behaviorType;
        String insightMessage;

        if (buyRatio > 0.7) {
            behaviorType = "aggressive";
            insightMessage = "You have an aggressive style, buying on almost every signal. Remember: sometimes the best trade is no trade.";
        } else if (buyRatio < 0.3) {
            behaviorType = "cautious";
            insightMessage = "You are very cautious and tend to wait for perfect clarity. Don't let fear of a pull-back stop you from catching a rally.";
        } else {
            behaviorType = "balanced";
            insightMessage = "You have a balanced approach, weighing signals carefully before committing capital. Keep this discipline.";
        }

        Map<String, String> response = new HashMap<>();
        response.put("behaviorType", behaviorType);
        response.put("insightMessage", "AI Analysis: " + insightMessage);
        
        return response;
    }

    @GetMapping
    public List<Decision> getAllDecisions() {
        return decisionRepo.findByUserId(getLoggedInUserId());
    }
}
