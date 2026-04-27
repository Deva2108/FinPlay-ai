package com.example.stockPortfolio.DecisionManagement;

import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class DecisionService {

    private final DecisionRepo decisionRepo;
    private final UserService userService;
    private final com.example.stockPortfolio.HoldingsManagement.TransactionRepo transactionRepo;

    private User getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.getUserByEmail(email);
    }

    public DecisionDTO saveDecision(DecisionDTO request) {
        User user = getLoggedInUser();
        Decision decision = new Decision();
        decision.setSymbol(request.getSymbol());
        decision.setAction(request.getAction());
        decision.setPrice(request.getPrice());
        decision.setMarket(request.getMarket());
        decision.setUser(user);
        if (request.getTimestamp() == null) {
            decision.setTimestamp(LocalDateTime.now());
        } else {
            decision.setTimestamp(request.getTimestamp());
        }
        return DecisionDTO.fromEntity(decisionRepo.save(decision));
    }

    public Map<String, String> evaluateDecision(Map<String, Object> request) {
        String action = (String) request.getOrDefault("action", "WAIT");
        boolean isPositive = (boolean) request.getOrDefault("isPositive", true);
        String pattern = (String) request.getOrDefault("pattern", "balanced");

        String aiResponse;
        String behaviorHighlight = "Pattern: " + pattern.toUpperCase();

        if (action.equalsIgnoreCase("BUY")) {
            if (isPositive) {
                if (pattern.equalsIgnoreCase("aggressive")) {
                    aiResponse = "Fast move. You’re striking while the momentum is clear, choosing participation over the risk of being left behind.";
                } else if (pattern.equalsIgnoreCase("cautious")) {
                    aiResponse = "Interesting move. Even with your cautious style, you've seen enough strength to step in.";
                } else {
                    aiResponse = "I see your thinking. This entry aligns with a balanced view of the current uptrend.";
                }
            } else {
                if (pattern.equalsIgnoreCase("aggressive")) {
                    aiResponse = "Interesting move. You’re betting on a fast reversal while others are still fearful.";
                } else {
                    aiResponse = "I see your thinking. You’re looking for deep value during this decline.";
                }
            }
        } else {
            if (isPositive) {
                if (pattern.equalsIgnoreCase("cautious")) {
                    aiResponse = "Interesting move. Your cautious approach is keeping you safe as the market tests these highs.";
                } else {
                    aiResponse = "I see your thinking. Holding back during a rally suggests you’re waiting for a more disciplined entry point.";
                }
            } else {
                aiResponse = "Interesting move. Staying liquid during a decline shows a calm awareness of risk.";
            }
        }

        Map<String, String> response = new HashMap<>();
        response.put("outcome", isPositive && action.equalsIgnoreCase("BUY") ? "good" : !isPositive && action.equalsIgnoreCase("BUY") ? "risky" : "neutral");
        response.put("aiMessage", aiResponse);
        response.put("behaviorHighlight", behaviorHighlight);
        
        return response;
    }

    public List<DecisionDTO> getAllDecisions() {
        return decisionRepo.findByUser_UserId(getLoggedInUser().getUserId()).stream()
                .map(DecisionDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<DecisionDTO> getRecentDecisions() {
        return decisionRepo.findTop10ByUser_UserIdOrderByTimestampDesc(getLoggedInUser().getUserId()).stream()
                .map(DecisionDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getStats() {
        Long userId = getLoggedInUser().getUserId();
        List<Decision> userDecisions = decisionRepo.findByUser_UserId(userId);
        
        long buys = userDecisions.stream().filter(d -> "BUY".equalsIgnoreCase(d.getAction())).count();
        long skips = userDecisions.stream().filter(d -> "SKIP".equalsIgnoreCase(d.getAction())).count();
        int total = userDecisions.size();

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
        
        // revenueBySector (Equivalent to revenueByGarmentType for a Stock App)
        List<com.example.stockPortfolio.HoldingsManagement.Transaction> txns = transactionRepo.findByUserId(userId);
        Map<String, Double> revenueBySector = new HashMap<>();
        
        txns.stream().filter(t -> t.getType() == com.example.stockPortfolio.HoldingsManagement.Transaction.TransactionType.SELL)
            .forEach(t -> {
                String sector = "Other";
                String sym = t.getSymbol().toUpperCase();
                if (sym.matches(".*(AAPL|MSFT|NVDA|GOOGL|META|AMD|INTC|CRM|AVGO).*")) sector = "Tech";
                else if (sym.matches(".*(JPM|V|MA|BAC|HDFC|ICICI).*")) sector = "Finance";
                else if (sym.matches(".*(TSLA|F|GM|TATA MOTORS).*")) sector = "Automotive";
                else if (sym.matches(".*(XOM|CVX|SLB|RELIANCE).*")) sector = "Energy";
                
                double amount = t.getPrice().multiply(java.math.BigDecimal.valueOf(t.getQuantity())).doubleValue();
                revenueBySector.put(sector, revenueBySector.getOrDefault(sector, 0.0) + amount);
            });
            
        stats.put("revenueBySector", revenueBySector);
        
        return stats;
    }

    public Map<String, String> getInsights() {
        Long userId = getLoggedInUser().getUserId();
        List<Decision> userDecisions = decisionRepo.findByUser_UserId(userId);
        
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
}
