package com.example.stockPortfolio.ContentManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Combines YouTube and iTunes lookups for a given insight topic and caches the
 * result in Redis so the Insights page can render embedded media without a
 * runtime external call.
 *
 * Cache key: content:{topic-slug}, TTL 24 hours.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContentService {

    private static final String CONTENT_KEY_PREFIX = "content:";
    private static final long TTL_HOURS = 24;
    private static final int MAX_VIDEOS = 3;
    private static final int MAX_PODCASTS = 3;

    private final YouTubeService youTubeService;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Cache-first read. Falls back to empty list on cold cache. */
    @SuppressWarnings("unchecked")
    public List<ContentItemDTO> getCachedContent(String topic) {
        String key = CONTENT_KEY_PREFIX + slug(topic);
        Object data = redisTemplate.opsForValue().get(key);
        if (data instanceof List) {
            List<ContentItemDTO> result = new ArrayList<>();
            for (Object item : (List<Object>) data) {
                if (item instanceof ContentItemDTO dto) result.add(dto);
                else if (item instanceof java.util.Map<?, ?> map) result.add(fromMap(map));
            }
            return result;
        }
        return List.of();
    }

    /** Scheduler hook: refreshes cache for a single topic. */
    public List<ContentItemDTO> refreshContentForTopic(String topic) {
        if (topic == null || topic.isBlank()) return List.of();

        List<ContentItemDTO> combined = new ArrayList<>();
        combined.addAll(youTubeService.searchEmbeddableVideos(topic, MAX_VIDEOS));

        if (!combined.isEmpty()) {
            String key = CONTENT_KEY_PREFIX + slug(topic);
            redisTemplate.opsForValue().set(key, combined, TTL_HOURS, TimeUnit.HOURS);
            log.info("Refreshed content cache for '{}' ({} items)", topic, combined.size());
        } else {
            log.debug("No content fetched for topic '{}'.", topic);
        }
        return combined;
    }

    private String slug(String topic) {
        if (topic == null || topic.isBlank()) return "default";
        return topic.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    @SuppressWarnings("unchecked")
    private ContentItemDTO fromMap(java.util.Map<?, ?> map) {
        return ContentItemDTO.builder()
                .type(asString(map.get("type")))
                .title(asString(map.get("title")))
                .url(asString(map.get("url")))
                .embedUrl(asString(map.get("embedUrl")))
                .thumbnailUrl(asString(map.get("thumbnailUrl")))
                .source(asString(map.get("source")))
                .publishedAt(map.get("publishedAt") instanceof Number n ? n.longValue() : null)
                .build();
    }

    private String asString(Object v) { return v == null ? null : v.toString(); }
}
