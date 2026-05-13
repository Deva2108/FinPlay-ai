package com.example.stockPortfolio.ContentManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Returns YouTube + iTunes media bundles attached to a FinPlay insight topic. */
@RestController
@RequestMapping("/api/content")
@Tag(name = "10. Content Layer", description = "YouTube + iTunes media bundles per insight topic")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ContentItemDTO>>> getContent(@RequestParam String topic) {
        List<ContentItemDTO> items = contentService.getCachedContent(topic);
        if (items.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.syncing(items, "Content for '" + topic + "' is syncing", "fallback"));
        }
        return ResponseEntity.ok(ApiResponse.ok(items, "Content fetched from cache", "cache"));
    }
}
