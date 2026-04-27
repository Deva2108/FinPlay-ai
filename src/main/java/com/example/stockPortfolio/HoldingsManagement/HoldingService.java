package com.example.stockPortfolio.HoldingsManagement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class HoldingService {

    private final HoldingRepo holdingRepo;
    private final TransactionRepo transactionRepo;
    private final FmpStockPriceService fmpService;
    private final com.example.stockPortfolio.PortfolioManagement.PortfolioService portfolioService;

    @Transactional
    public void processTransaction(Transaction txn) {
        Optional<Holding> optionalHolding = holdingRepo
                .findByUserIdAndPortfolioId(txn.getUserId(), txn.getPortfolioId())
                .stream()
                .filter(h -> h.getSymbol().equalsIgnoreCase(txn.getSymbol()))
                .findFirst();

        Holding holding = optionalHolding.orElse(null);
        double transactionAmount = txn.getQuantity() * txn.getPrice().doubleValue();

        if (txn.getType() == Transaction.TransactionType.BUY) {
            portfolioService.updateBalance(txn.getPortfolioId(), java.math.BigDecimal.valueOf(-transactionAmount), txn.getUserId());
            if (holding == null) {
                holding = new Holding();
                holding.setUserId(txn.getUserId());
                holding.setPortfolioId(txn.getPortfolioId());
                holding.setSymbol(txn.getSymbol());
                holding.setQuantity(txn.getQuantity());
                holding.setBuyPrice(txn.getPrice());
            } else {
                int totalQty = holding.getQuantity() + txn.getQuantity();
                double newAvgPrice = ((holding.getBuyPrice().doubleValue() * holding.getQuantity()) +
                        (txn.getPrice().doubleValue() * txn.getQuantity())) / totalQty;
                holding.setBuyPrice(java.math.BigDecimal.valueOf(newAvgPrice));
                holding.setQuantity(totalQty);
            }
            holdingRepo.save(holding);
        } else if (txn.getType() == Transaction.TransactionType.SELL) {
            if (holding == null || holding.getQuantity() < txn.getQuantity()) {
                throw new IllegalArgumentException("Insufficient quantity to sell.");
            }

            portfolioService.updateBalance(txn.getPortfolioId(), java.math.BigDecimal.valueOf(transactionAmount), txn.getUserId());
            holding.setQuantity(holding.getQuantity() - txn.getQuantity());
            if (holding.getQuantity() == 0) {
                holdingRepo.delete(holding);
            } else {
                holdingRepo.save(holding);
            }
        }
        transactionRepo.save(txn);
    }

    public HoldingResponseDTO getHoldingsWithDetails(Long userId, Long portfolioId) {
        List<Holding> holdings = holdingRepo.findByUserIdAndPortfolioId(userId, portfolioId);
        
        List<CompletableFuture<HoldingStatusDTO>> futures = holdings.stream()
            .map(h -> CompletableFuture.supplyAsync(() -> {
                com.fasterxml.jackson.databind.JsonNode quote = fmpService.getFullQuote(h.getSymbol());
                if (quote != null && quote.has("price")) {
                    double currentPrice = quote.get("price").asDouble();
                    HoldingStatusDTO dto = new HoldingStatusDTO();
                    dto.setSymbol(h.getSymbol());
                    dto.setCompanyName(quote.has("name") ? quote.get("name").asText() : "N/A");
                    dto.setSector("N/A");
                    dto.setQuantity(h.getQuantity());
                    dto.setBuyPrice(h.getBuyPrice());
                    dto.setCurrentPrice(java.math.BigDecimal.valueOf(currentPrice));

                    double gain = (currentPrice - h.getBuyPrice().doubleValue()) * h.getQuantity();
                    dto.setGain(java.math.BigDecimal.valueOf(gain));
                    dto.setGainPercentage(java.math.BigDecimal.valueOf((gain / (h.getBuyPrice().doubleValue() * h.getQuantity())) * 100));
                    return dto;
                }
                return null;
            }))
            .collect(Collectors.toList());

        List<HoldingStatusDTO> statusList = futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        double totalValue = statusList.stream()
            .mapToDouble(dto -> dto.getCurrentPrice().doubleValue() * dto.getQuantity())
            .sum();

        return new HoldingResponseDTO(statusList, totalValue, 200, "Live FMP holdings fetched in parallel.");
    }

    public List<TransactionDTO> getAllTransactions(Long userId, Long portfolioId) {
        List<Transaction> allTransactions = transactionRepo.findByUserIdAndPortfolioIdOrderByTransactionDateDesc(userId, portfolioId);
        
        // Rolling average map: Symbol -> [TotalQuantity, TotalCost]
        Map<String, double[]> rollingAvgs = new HashMap<>();
        
        // We need them in chronological order to calculate rolling average correctly
        List<Transaction> chronological = allTransactions.stream()
            .sorted(Comparator.comparing(Transaction::getTransactionDate))
            .collect(Collectors.toList());

        Map<Long, Double> transactionGains = new HashMap<>();
        Map<Long, Double> transactionGainPercentages = new HashMap<>();

        for (Transaction txn : chronological) {
            String symbol = txn.getSymbol().toUpperCase();
            double[] stats = rollingAvgs.computeIfAbsent(symbol, k -> new double[]{0.0, 0.0});
            
            if (txn.getType() == Transaction.TransactionType.BUY) {
                stats[0] += txn.getQuantity();
                stats[1] += txn.getPrice().multiply(java.math.BigDecimal.valueOf(txn.getQuantity())).doubleValue();
            } else {
                if (stats[0] > 0) {
                    double avgBuyPrice = stats[1] / stats[0];
                    double gain = (txn.getPrice().doubleValue() - avgBuyPrice) * txn.getQuantity();
                    double gainPercentage = (gain / (avgBuyPrice * txn.getQuantity())) * 100;
                    
                    transactionGains.put(txn.getTransactionId(), gain);
                    transactionGainPercentages.put(txn.getTransactionId(), gainPercentage);
                    
                    // Update stats for sell (FIFO/Avg cost)
                    stats[0] -= txn.getQuantity();
                    stats[1] -= txn.getQuantity() * avgBuyPrice;
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
            dto.setPrice(txn.getPrice());
            dto.setType(txn.getType().name());
            dto.setTransactionDate(txn.getTransactionDate());
            dto.setPaymentStatus(txn.getPaymentStatus() != null ? txn.getPaymentStatus().name() : "PAID");
            dto.setNotes(txn.getNotes());
            dto.setCreatedAt(txn.getCreatedAt());
            dto.setUpdatedAt(txn.getUpdatedAt());

            if (txn.getType() == Transaction.TransactionType.SELL) {
                Double gain = transactionGains.getOrDefault(txn.getTransactionId(), 0.0);
                Double gainPct = transactionGainPercentages.getOrDefault(txn.getTransactionId(), 0.0);
                
                dto.setGain(java.math.BigDecimal.valueOf(gain));
                dto.setGainPercentage(java.math.BigDecimal.valueOf(gainPct));
                dto.setLoss(java.math.BigDecimal.valueOf(gain < 0 ? -gain : 0.0));
                dto.setLossPercentage(java.math.BigDecimal.valueOf(gain < 0 ? -gainPct : 0.0));
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
}
