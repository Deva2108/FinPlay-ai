package com.example.stockPortfolio.ContentManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the YouTube Data API v3 search endpoint and returns embeddable videos
 * suitable for iframe playback inside the Insights page.
 *
 * Docs: https://developers.google.com/youtube/v3/docs/search/list
 *
 * Only invoked from the scheduler / ContentService — never from a request
 * thread, so we don't add a per-call rate limiter (YouTube's free 10k unit/day
 * quota is the binding constraint, not RPM).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class YouTubeService {

    private static final String SEARCH_URL = "https://www.googleapis.com/youtube/v3/search";

    private final RestTemplate restTemplate;

    @Value("${YOUTUBE_API_KEY:${youtube.api.key:}}")
    private String apiKey;

    @PostConstruct
    public void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("YouTube API key missing. Video lookups will return empty.");
        }
    }

    /** Returns up to {@code maxResults} embeddable videos for the given keyword. */
    public List<ContentItemDTO> searchEmbeddableVideos(String query, int maxResults) {
        if (apiKey == null || apiKey.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }
        int capped = Math.max(1, Math.min(maxResults, 5));

        String url = UriComponentsBuilder.fromHttpUrl(SEARCH_URL)
                .queryParam("part", "snippet")
                .queryParam("type", "video")
                .queryParam("videoEmbeddable", "true")
                .queryParam("safeSearch", "strict")
                .queryParam("maxResults", capped)
                .queryParam("q", query)
                .queryParam("key", apiKey)
                .toUriString();

        try {
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return List.of();

            List<?> items = (List<?>) response.get("items");
            if (items == null || items.isEmpty()) return List.of();

            List<ContentItemDTO> results = new ArrayList<>();
            for (Object raw : items) {
                if (!(raw instanceof Map<?, ?> item)) continue;
                Map<?, ?> id = (Map<?, ?>) item.get("id");
                Map<?, ?> snippet = (Map<?, ?>) item.get("snippet");
                if (id == null || snippet == null) continue;

                String videoId = (String) id.get("videoId");
                if (videoId == null) continue;

                String title = (String) snippet.get("title");
                String channel = (String) snippet.get("channelTitle");
                String publishedAt = (String) snippet.get("publishedAt");
                String thumbnail = pickThumbnail(snippet);

                results.add(ContentItemDTO.builder()
                        .type("video")
                        .title(title)
                        .source(channel)
                        .url("https://www.youtube.com/watch?v=" + videoId)
                        .embedUrl("https://www.youtube.com/embed/" + videoId)
                        .thumbnailUrl(thumbnail)
                        .publishedAt(parseEpoch(publishedAt))
                        .build());
            }
            return results;
        } catch (Exception ex) {
            log.warn("YouTube search failed for '{}': {}", query, ex.getMessage());
            return List.of();
        }
    }

    private String pickThumbnail(Map<?, ?> snippet) {
        Map<?, ?> thumbnails = (Map<?, ?>) snippet.get("thumbnails");
        if (thumbnails == null) return null;
        for (String key : new String[] {"medium", "high", "default"}) {
            Object node = thumbnails.get(key);
            if (node instanceof Map<?, ?> nested) {
                Object urlVal = nested.get("url");
                if (urlVal != null) return urlVal.toString();
            }
        }
        return null;
    }

    private Long parseEpoch(String iso) {
        if (iso == null) return null;
        try { return Instant.parse(iso).getEpochSecond(); } catch (Exception ex) { return null; }
    }
}
