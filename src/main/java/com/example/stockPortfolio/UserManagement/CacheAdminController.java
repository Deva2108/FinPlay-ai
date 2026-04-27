package com.example.stockPortfolio.UserManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/cache")
@Tag(name = "5. Admin", description = "Cache Management")
public class CacheAdminController {

    @DeleteMapping("/clear")
    @CacheEvict(value = "stockPrices", allEntries = true)
    public ResponseEntity<ApiResponse<String>> clearStockCache() {
        return ResponseEntity.ok(ApiResponse.ok("Stock prices cache cleared successfully.", "Success"));
    }
}
