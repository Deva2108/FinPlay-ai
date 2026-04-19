package com.example.stockPortfolio.HoldingsManagement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HoldingService {

    private final HoldingRepo holdingRepo;
    private final TransactionRepo transactionRepo;
    private final FmpStockPriceService fmpService;

    @Transactional
    public void processTransaction(Transaction txn) {
        Optional<Holding> optionalHolding = holdingRepo
                .findByUserIdAndPortfolioId(txn.getUserId(), txn.getPortfolioId())
                .stream()
                .filter(h -> h.getSymbol().equalsIgnoreCase(txn.getSymbol()))
                .findFirst();

        Holding holding = optionalHolding.orElse(null);

        if (txn.getType() == Transaction.TransactionType.BUY) {
            if (holding == null) {
                holding = new Holding();
                holding.setUserId(txn.getUserId());
                holding.setPortfolioId(txn.getPortfolioId());
                holding.setSymbol(txn.getSymbol());
                holding.setQuantity(txn.getQuantity());
                holding.setBuyPrice(txn.getPrice());
            } else {
                int totalQty = holding.getQuantity() + txn.getQuantity();
                double newAvgPrice = ((holding.getBuyPrice() * holding.getQuantity()) +
                        (txn.getPrice() * txn.getQuantity())) / totalQty;
                holding.setBuyPrice(newAvgPrice);
                holding.setQuantity(totalQty);
            }
            holdingRepo.save(holding);
        } else if (txn.getType() == Transaction.TransactionType.SELL) {
            if (holding == null || holding.getQuantity() < txn.getQuantity()) {
                throw new IllegalArgumentException("Insufficient quantity to sell.");
            }

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
        List<HoldingStatusDTO> statusList = new ArrayList<>();
        double totalValue = 0.0;

        for (Holding h : holdings) {
            com.fasterxml.jackson.databind.JsonNode quote = fmpService.getFullQuote(h.getSymbol());

            if (quote != null && quote.has("price")) {
                double currentPrice = quote.get("price").asDouble();
                HoldingStatusDTO dto = new HoldingStatusDTO();
                dto.setSymbol(h.getSymbol());
                dto.setCompanyName(quote.has("name") ? quote.get("name").asText() : "N/A");
                dto.setSector("N/A");
                dto.setQuantity(h.getQuantity());
                dto.setBuyPrice(h.getBuyPrice());
                dto.setCurrentPrice(currentPrice);

                double gain = (currentPrice - h.getBuyPrice()) * h.getQuantity();
                dto.setGain(gain);
                dto.setGainPercentage((gain / (h.getBuyPrice() * h.getQuantity())) * 100);

                totalValue += currentPrice * h.getQuantity();
                statusList.add(dto);
            }
        }

        return new HoldingResponseDTO(statusList, totalValue, 200, "Live FMP holdings fetched.");
    }

    public List<TransactionDTO> getAllTransactions(Long userId, Long portfolioId) {
        List<Transaction> allTransactions = transactionRepo.findByUserIdAndPortfolioIdOrderByTransactionDateDesc(userId, portfolioId);

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

            if (txn.getType() == Transaction.TransactionType.SELL) {
                double totalBuyQty = 0;
                double totalBuyCost = 0;

                for (Transaction t : allTransactions) {
                    if (t.getSymbol().equalsIgnoreCase(txn.getSymbol())
                            && t.getType() == Transaction.TransactionType.BUY
                            && t.getTransactionDate().isBefore(txn.getTransactionDate())) {
                        totalBuyQty += t.getQuantity();
                        totalBuyCost += t.getQuantity() * t.getPrice();
                    }
                }

                if (totalBuyQty > 0) {
                    double avgBuyPrice = totalBuyCost / totalBuyQty;
                    double gain = (txn.getPrice() - avgBuyPrice) * txn.getQuantity();
                    double gainPercentage = (gain / (avgBuyPrice * txn.getQuantity())) * 100;

                    dto.setGain(gain);
                    dto.setGainPercentage(gainPercentage);

                    if (gain < 0) {
                        dto.setLoss(-gain);
                        dto.setLossPercentage(-gainPercentage);
                    } else {
                        dto.setLoss(0.0);
                        dto.setLossPercentage(0.0);
                    }
                }
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void deleteHolding(Long id) {
        Holding holding = holdingRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Holding not found with id: " + id));
        holdingRepo.delete(holding);
    }
}
