package com.example.stockPortfolio.TradingManagement;

import com.example.stockPortfolio.HoldingsManagement.HoldingResponseDTO;
import com.example.stockPortfolio.HoldingsManagement.HoldingService;
import com.example.stockPortfolio.HoldingsManagement.HoldingStatusDTO;
import com.example.stockPortfolio.HoldingsManagement.Transaction;
import com.example.stockPortfolio.HoldingsManagement.TransactionDTO;
import com.example.stockPortfolio.MarketManagement.MarketGateway;
import com.example.stockPortfolio.PortfolioManagement.PortfolioDTO;
import com.example.stockPortfolio.PortfolioManagement.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-house paper trading simulator (FinPlay's replacement for Alpaca).
 *
 * Adapter layer: delegates all real work to existing services
 *   - {@link PortfolioService} for cash/balance management
 *   - {@link HoldingService} for buy/sell + mark-to-market
 *   - {@link MarketGateway} for live quote lookups when caller didn't pin a price
 *
 * No external broker, no signup. The Alpaca-shaped DTOs ({@link SimulatedAccountDTO},
 * {@link SimulatedPositionDTO}, {@link SimulatedOrderDTO}) keep the frontend
 * integration plan from the FinPlay exec summary intact.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaperTradingService {

    private final PortfolioService portfolioService;
    private final HoldingService holdingService;
    private final MarketGateway marketGateway;
    private final com.example.stockPortfolio.MarketManagement.SymbolNormalizer symbolNormalizer;
    private final com.example.stockPortfolio.MarketManagement.ForexService forexService;

    // -------- account summary --------

    public SimulatedAccountDTO getAccount(Long portfolioId, Long userId) {
        PortfolioDTO portfolio = portfolioService.getPortfolioById(portfolioId, userId);
        HoldingResponseDTO holdings = holdingService.getHoldingsWithDetails(userId, portfolioId);

        BigDecimal cash = (portfolio.getBalance() == null ? BigDecimal.ZERO : portfolio.getBalance()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal positionsValue = holdings.getTotalValue().setScale(2, RoundingMode.HALF_UP);
        BigDecimal equity = cash.add(positionsValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal initial = (portfolio.getInitialBalance() == null ? BigDecimal.ZERO : portfolio.getInitialBalance()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalReturn = equity.subtract(initial).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalReturnPct = initial.signum() == 0
                ? BigDecimal.ZERO
                : totalReturn.multiply(BigDecimal.valueOf(100)).divide(initial, 2, RoundingMode.HALF_UP);

        return SimulatedAccountDTO.builder()
                .portfolioId(portfolioId)
                .currency("INR")
                .cash(cash)
                .positionsValue(positionsValue)
                .equity(equity)
                .portfolioValue(equity)
                .buyingPower(cash)
                .initialBalance(initial)
                .totalReturn(totalReturn)
                .totalReturnPct(totalReturnPct)
                .build();
    }

    // -------- positions --------

    public List<SimulatedPositionDTO> getPositions(Long portfolioId, Long userId) {
        HoldingResponseDTO holdings = holdingService.getHoldingsWithDetails(userId, portfolioId);
        if (holdings == null || holdings.getHoldings() == null) return List.of();

        List<SimulatedPositionDTO> result = new ArrayList<>();
        for (HoldingStatusDTO h : holdings.getHoldings()) {
            BigDecimal qty = h.getQuantity();
            BigDecimal costBasis = (h.getBuyPrice() == null ? BigDecimal.ZERO : h.getBuyPrice().multiply(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal marketValue = (h.getCurrentPrice() == null ? BigDecimal.ZERO : h.getCurrentPrice().multiply(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal pl = marketValue.subtract(costBasis).setScale(2, RoundingMode.HALF_UP);
            BigDecimal plPct = costBasis.signum() == 0
                    ? BigDecimal.ZERO
                    : pl.multiply(BigDecimal.valueOf(100)).divide(costBasis, 2, RoundingMode.HALF_UP);

            result.add(SimulatedPositionDTO.builder()
                    .symbol(h.getSymbol())
                    .companyName(h.getCompanyName())
                    .sector(h.getSector())
                    .currency("INR")
                    .quantity(h.getQuantity())
                    .avgEntryPrice(h.getBuyPrice().setScale(2, RoundingMode.HALF_UP))
                    .currentPrice(h.getCurrentPrice().setScale(2, RoundingMode.HALF_UP))
                    .costBasis(costBasis)
                    .marketValue(marketValue)
                    .unrealizedPl(pl)
                    .unrealizedPlpc(plPct)
                    .build());
        }
        return result;
    }

    // -------- orders --------

    public List<SimulatedOrderDTO> getOrders(Long portfolioId, Long userId) {
        List<TransactionDTO> txns = holdingService.getAllTransactions(userId, portfolioId);
        List<SimulatedOrderDTO> result = new ArrayList<>();
        for (TransactionDTO t : txns) {
            result.add(SimulatedOrderDTO.builder()
                    .id(t.getTransactionId())
                    .symbol(t.getSymbol())
                    .side(t.getType())
                    .type("MARKET")
                    .status("FILLED")
                    .currency("INR")
                    .quantity(t.getQuantity())
                    .price(t.getPrice().setScale(2, RoundingMode.HALF_UP))
                    .placedAt(t.getTransactionDate())
                    .filledAt(t.getTransactionDate())
                    .gain(t.getGain() != null ? t.getGain().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .gainPercentage(t.getGainPercentage() != null ? t.getGainPercentage().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .notes(t.getNotes())
                    .build());
        }
        return result;
    }

    @org.springframework.transaction.annotation.Transactional
    public SimulatedOrderDTO placeOrder(SimulatedOrderRequestDTO req, Long userId) {
        portfolioService.validatePortfolioOwnership(req.getPortfolioId(), userId);

        String normalized = symbolNormalizer.normalize(req.getSymbol());
        if (normalized == null) {
            throw new IllegalArgumentException("Unsupported or invalid symbol: " + req.getSymbol());
        }

        // ALWAYS fetch live price from Gateway; never trust client price for fills.
        BigDecimal rawPrice = currentPrice(normalized);
        if (rawPrice == null) {
            throw new IllegalStateException("No price available for symbol " + normalized + " — try again in a moment.");
        }

        // CONVERSION LOGIC: Convert USD to INR if necessary
        BigDecimal fillPriceInInr = rawPrice;
        if (!symbolNormalizer.isIndian(normalized)) {
            fillPriceInInr = forexService.convertUsdToInr(rawPrice);
            log.debug("Converted US stock order price {} USD to {} INR", rawPrice, fillPriceInInr);
        }

        Transaction txn = new Transaction();
        txn.setUserId(userId);
        txn.setPortfolioId(req.getPortfolioId());
        txn.setSymbol(normalized);
        txn.setQuantity(req.getQuantity());
        txn.setPrice(fillPriceInInr.setScale(2, RoundingMode.HALF_UP));
        txn.setTransactionDate(LocalDateTime.now());
        txn.setType(Transaction.TransactionType.valueOf(req.getSide().toUpperCase()));
        txn.setNotes(req.getNotes());

        holdingService.processTransaction(txn);

        return SimulatedOrderDTO.builder()
                .id(txn.getTransactionId())
                .symbol(txn.getSymbol())
                .side(txn.getType().name())
                .type("MARKET")
                .status("FILLED")
                .currency("INR")
                .quantity(txn.getQuantity())
                .price(txn.getPrice())
                .placedAt(txn.getTransactionDate())
                .filledAt(txn.getTransactionDate())
                .notes(txn.getNotes())
                .build();
    }

    private BigDecimal currentPrice(String symbol) {
        try {
            var resp = marketGateway.getLatestQuote(symbol);
            if (resp == null || resp.getData() == null) {
                log.warn("MarketGateway returned no data for {} (cache miss + upstream unavailable)", symbol);
                return null;
            }
            Map<String, Object> data = resp.getData();
            Object price = data.get("price");
            if (price == null) {
                log.warn("Quote for {} has no 'price' field: {}", symbol, data.keySet());
                return null;
            }
            BigDecimal parsed = new BigDecimal(price.toString());
            if (parsed.signum() <= 0) {
                log.warn("Quote for {} returned non-positive price {}; rejecting", symbol, parsed);
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            log.warn("Quote for {} has unparsable price: {}", symbol, ex.getMessage());
        } catch (Exception ex) {
            log.warn("Could not resolve current price for {}: {}", symbol, ex.getMessage());
        }
        return null;
    }
}
