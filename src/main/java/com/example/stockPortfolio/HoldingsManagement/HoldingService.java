package com.example.stockPortfolio.HoldingsManagement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import java.util.concurrent.CompletableFuture;

import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class HoldingService {

    private final HoldingRepo holdingRepo;
    private final TransactionRepo transactionRepo;
    private final com.example.stockPortfolio.MarketManagement.MarketGateway marketGateway;
    private final com.example.stockPortfolio.PortfolioManagement.PortfolioService portfolioService;
    private final com.example.stockPortfolio.MarketManagement.MarketAnalysisService marketAnalysisService;
    private final com.example.stockPortfolio.MarketManagement.ForexService forexService;

    @Transactional
    public void processTransaction(Transaction txn) {
        // ---- defensive guards (controller-layer @Valid is the first line; this is the second) ----
        if (txn == null) {
            throw new IllegalArgumentException("Transaction must not be null.");
        }
        if (txn.getUserId() == null || txn.getPortfolioId() == null) {
            throw new IllegalArgumentException("userId and portfolioId are required.");
        }
        if (txn.getSymbol() == null || txn.getSymbol().isBlank()) {
            throw new IllegalArgumentException("symbol is required.");
        }
        if (txn.getQuantity() == null || txn.getQuantity().signum() <= 0) {
            throw new IllegalArgumentException("quantity must be > 0.");
        }
        if (txn.getPrice() == null || txn.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("price must be > 0.");
        }
        if (txn.getType() == null) {
            throw new IllegalArgumentException("transaction type (BUY/SELL) is required.");
        }
        // Normalize symbol once to avoid case-mismatch with the Holding row + market cache.
        txn.setSymbol(txn.getSymbol().trim().toUpperCase());

        // Use pessimistic locking to prevent race conditions
        Holding holding = holdingRepo
                .findByUserIdAndPortfolioIdAndSymbolWithLock(txn.getUserId(), txn.getPortfolioId(), txn.getSymbol())
                .orElse(null);

        java.math.BigDecimal transactionAmount = txn.getQuantity().multiply(txn.getPrice());

        if (txn.getType() == Transaction.TransactionType.BUY) {
            portfolioService.updateBalance(txn.getPortfolioId(), transactionAmount.negate(), txn.getUserId());
            if (holding == null) {
                holding = new Holding();
                holding.setUserId(txn.getUserId());
                holding.setPortfolioId(txn.getPortfolioId());
                holding.setSymbol(txn.getSymbol());
                holding.setQuantity(txn.getQuantity());
                holding.setBuyPrice(txn.getPrice());
            } else {
                java.math.BigDecimal totalQty = holding.getQuantity().add(txn.getQuantity());
                java.math.BigDecimal totalCost = holding.getBuyPrice().multiply(holding.getQuantity())
                        .add(txn.getPrice().multiply(txn.getQuantity()));
                java.math.BigDecimal newAvgPrice = totalCost.divide(totalQty, 4, RoundingMode.HALF_UP);
                
                holding.setBuyPrice(newAvgPrice);
                holding.setQuantity(totalQty);
            }
            holdingRepo.save(holding);
            marketGateway.markAsPriority(txn.getSymbol());
        } else if (txn.getType() == Transaction.TransactionType.SELL) {
            if (holding == null || holding.getQuantity().compareTo(txn.getQuantity()) < 0) {
                throw new IllegalArgumentException("Insufficient quantity to sell.");
            }

            portfolioService.updateBalance(txn.getPortfolioId(), transactionAmount, txn.getUserId());
            holding.setQuantity(holding.getQuantity().subtract(txn.getQuantity()));
            if (holding.getQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                holdingRepo.delete(holding);
            } else {
                holdingRepo.save(holding);
            }
        }
        transactionRepo.save(txn);
    }

    public HoldingResponseDTO getHoldingsWithDetails(Long userId, Long portfolioId) {
        List<Holding> holdings = holdingRepo.findByUserIdAndPortfolioId(userId, portfolioId);
        
        List<String> symbols = holdings.stream().map(Holding::getSymbol).toList();
        Map<String, Map<String, Object>> quotes = marketGateway.getBatchQuotes(symbols);

        List<HoldingStatusDTO> statusList = holdings.stream()
            .map(h -> {
                Map<String, Object> quote = quotes.get(h.getSymbol());

                if (quote != null && quote.containsKey("price")) {
                    java.math.BigDecimal rawPrice = new java.math.BigDecimal(quote.get("price").toString());
                    
                    // CONVERSION LOGIC: If stock is US, convert price to INR
                    java.math.BigDecimal currentPriceInInr = rawPrice;
                    if (!marketGateway.getSymbolNormalizer().isIndian(h.getSymbol())) {
                        currentPriceInInr = forexService.convertUsdToInr(rawPrice);
                    }
                    currentPriceInInr = currentPriceInInr.setScale(2, RoundingMode.HALF_UP);

                    HoldingStatusDTO dto = new HoldingStatusDTO();
                    dto.setSymbol(h.getSymbol());
                    dto.setCompanyName(quote.getOrDefault("name", quote.getOrDefault("companyName", h.getSymbol())).toString());
                    dto.setSector(marketAnalysisService.getSectorForSymbol(h.getSymbol()));
                    dto.setMarket(marketGateway.getSymbolNormalizer().isIndian(h.getSymbol()) ? "INDIA" : "US");
                    dto.setCurrency("INR");
                    dto.setQuantity(h.getQuantity());
                    dto.setBuyPrice(h.getBuyPrice().setScale(2, RoundingMode.HALF_UP));
                    dto.setCurrentPrice(currentPriceInInr);

                    java.math.BigDecimal gain = currentPriceInInr.subtract(h.getBuyPrice()).multiply(h.getQuantity())
                            .setScale(2, RoundingMode.HALF_UP);
                    java.math.BigDecimal cost = h.getBuyPrice().multiply(h.getQuantity());
                    
                    dto.setGain(gain);
                    if (cost.compareTo(java.math.BigDecimal.ZERO) != 0) {
                        dto.setGainPercentage(gain.divide(cost, 4, RoundingMode.HALF_UP).multiply(new java.math.BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP));
                    } else {
                        dto.setGainPercentage(java.math.BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    }
                    return dto;
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        java.math.BigDecimal totalValue = statusList.stream()
            .map(dto -> dto.getCurrentPrice().multiply(dto.getQuantity()))
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        return new HoldingResponseDTO(statusList, totalValue, 200, "Holdings fetched from local mirror.");
    }

    public org.springframework.data.domain.Page<TransactionDTO> getPagedTransactions(Long userId, Long portfolioId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Transaction> page = transactionRepo.findByUserIdAndPortfolioId(userId, portfolioId, pageable);
        
        return page.map(txn -> {
            TransactionDTO dto = new TransactionDTO();
            dto.setTransactionId(txn.getTransactionId());
            dto.setUserId(txn.getUserId());
            dto.setPortfolioId(txn.getPortfolioId());
            dto.setSymbol(txn.getSymbol());
            dto.setQuantity(txn.getQuantity());
            dto.setPrice(txn.getPrice().setScale(2, java.math.RoundingMode.HALF_UP));
            dto.setCurrency("INR");
            dto.setType(txn.getType().name());
            dto.setTransactionDate(txn.getTransactionDate());
            dto.setPaymentStatus(txn.getPaymentStatus() != null ? txn.getPaymentStatus().name() : "PAID");
            dto.setNotes(txn.getNotes());
            dto.setCreatedAt(txn.getCreatedAt());
            dto.setUpdatedAt(txn.getUpdatedAt());
            
            // For now, return zero for gains in paginated view to avoid heavy O(N) re-calculation.
            // In a real OMS, gain is stored on the Transaction record at fill time.
            dto.setGain(java.math.BigDecimal.ZERO);
            dto.setGainPercentage(java.math.BigDecimal.ZERO);
            
            return dto;
        });
    }

    public List<TransactionDTO> getAllTransactions(Long userId, Long portfolioId) {
        List<Transaction> allTransactions = transactionRepo.findByUserIdAndPortfolioIdOrderByTransactionDateDesc(userId, portfolioId);
        
        // Rolling average map: Symbol -> [TotalQuantity (BigDecimal), TotalCost (BigDecimal)]
        Map<String, java.math.BigDecimal[]> rollingAvgs = new HashMap<>();
        
        // We need them in chronological order to calculate rolling average correctly
        List<Transaction> chronological = allTransactions.stream()
            .sorted(Comparator.comparing(Transaction::getTransactionDate))
            .collect(Collectors.toList());

        Map<Long, java.math.BigDecimal> transactionGains = new HashMap<>();
        Map<Long, java.math.BigDecimal> transactionGainPercentages = new HashMap<>();

        for (Transaction txn : chronological) {
            String symbol = txn.getSymbol().toUpperCase();
            java.math.BigDecimal[] stats = rollingAvgs.computeIfAbsent(symbol, k -> new java.math.BigDecimal[]{java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO});
            
            if (txn.getType() == Transaction.TransactionType.BUY) {
                stats[0] = stats[0].add(txn.getQuantity());
                stats[1] = stats[1].add(txn.getPrice().multiply(txn.getQuantity()));
            } else {
                if (stats[0].compareTo(java.math.BigDecimal.ZERO) > 0) {
                    java.math.BigDecimal avgBuyPrice = stats[1].divide(stats[0], 4, RoundingMode.HALF_UP);
                    java.math.BigDecimal gain = txn.getPrice().subtract(avgBuyPrice).multiply(txn.getQuantity())
                            .setScale(2, RoundingMode.HALF_UP);
                    java.math.BigDecimal cost = avgBuyPrice.multiply(txn.getQuantity());
                    
                    transactionGains.put(txn.getTransactionId(), gain);
                    if (cost.compareTo(java.math.BigDecimal.ZERO) != 0) {
                        transactionGainPercentages.put(txn.getTransactionId(), gain.divide(cost, 4, RoundingMode.HALF_UP).multiply(new java.math.BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP));
                    }
                    
                    // Update stats for sell (Avg cost)
                    stats[0] = stats[0].subtract(txn.getQuantity());
                    stats[1] = stats[1].subtract(txn.getQuantity().multiply(avgBuyPrice));
                }
            }
        }

        return allTransactions.stream().map(txn -> {
            TransactionDTO dto = new TransactionDTO();
            dto.setTransactionId(txn.getTransactionId());
            dto.setUserId(txn.getUserId());
            dto.setPortfolioId(txn.getPortfolioId());
            dto.setSymbol(txn.getSymbol());
            dto.setQuantity(txn.getQuantity());
            dto.setPrice(txn.getPrice().setScale(2, RoundingMode.HALF_UP));
            dto.setCurrency("INR");
            dto.setType(txn.getType().name());
            dto.setTransactionDate(txn.getTransactionDate());
            dto.setPaymentStatus(txn.getPaymentStatus() != null ? txn.getPaymentStatus().name() : "PAID");
            dto.setNotes(txn.getNotes());
            dto.setCreatedAt(txn.getCreatedAt());
            dto.setUpdatedAt(txn.getUpdatedAt());

            if (txn.getType() == Transaction.TransactionType.SELL) {
                java.math.BigDecimal gain = transactionGains.getOrDefault(txn.getTransactionId(), java.math.BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
                java.math.BigDecimal gainPct = transactionGainPercentages.getOrDefault(txn.getTransactionId(), java.math.BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
                
                dto.setGain(gain);
                dto.setGainPercentage(gainPct);
                dto.setLoss(gain.compareTo(java.math.BigDecimal.ZERO) < 0 ? gain.negate().setScale(2, RoundingMode.HALF_UP) : java.math.BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                dto.setLossPercentage(gain.compareTo(java.math.BigDecimal.ZERO) < 0 ? gainPct.negate().setScale(2, RoundingMode.HALF_UP) : java.math.BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void deleteHolding(Long id, Long userId) {
        Holding holding = holdingRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Holding not found with id: " + id));
        if (!holding.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized to delete this holding.");
        }
        holdingRepo.delete(holding);
    }

    /**
     * Calculates user exposure to a specific symbol as a percentage (0-100).
     */
    public double getUserExposure(Long userId, String symbol) {
        List<Holding> holdings = holdingRepo.findByUserIdAndPortfolioId(userId, 1L); // Assuming portfolioId 1 for now or could be passed
        if (holdings.isEmpty()) return 0.0;

        List<String> symbols = holdings.stream().map(Holding::getSymbol).toList();
        Map<String, Map<String, Object>> quotes = marketGateway.getBatchQuotes(symbols);

        java.math.BigDecimal totalValue = java.math.BigDecimal.ZERO;
        java.math.BigDecimal targetValue = java.math.BigDecimal.ZERO;

        for (Holding h : holdings) {
            Map<String, Object> quote = quotes.get(h.getSymbol());
            if (quote != null && quote.containsKey("price")) {
                java.math.BigDecimal rawPrice = new java.math.BigDecimal(quote.get("price").toString());
                java.math.BigDecimal priceInInr = rawPrice;
                if (!marketGateway.getSymbolNormalizer().isIndian(h.getSymbol())) {
                    priceInInr = forexService.convertUsdToInr(rawPrice);
                }
                
                java.math.BigDecimal value = priceInInr.multiply(h.getQuantity());
                totalValue = totalValue.add(value);
                if (h.getSymbol().equalsIgnoreCase(symbol)) {
                    targetValue = value;
                }
            }
        }

        if (totalValue.compareTo(java.math.BigDecimal.ZERO) > 0) {
            return targetValue.divide(totalValue, 4, RoundingMode.HALF_UP).multiply(new java.math.BigDecimal("100")).doubleValue();
        }
        return 0.0;
    }

}
