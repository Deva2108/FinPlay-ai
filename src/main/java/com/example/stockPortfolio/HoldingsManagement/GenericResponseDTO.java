package com.example.stockPortfolio.HoldingsManagement;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class GenericResponseDTO {
    private Map<String, Object> details;
}
