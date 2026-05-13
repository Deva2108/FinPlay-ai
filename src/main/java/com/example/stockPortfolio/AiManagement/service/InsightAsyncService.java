package com.example.stockPortfolio.AiManagement.service;

import com.example.stockPortfolio.AiManagement.InsightStatusDTO;
import com.example.stockPortfolio.AiManagement.RichInsightDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Two-phase, non-blocking insight generator.
 *
 * Endpoints (see {@link com.example.stockPortfolio.AiManagement.InsightController}) return
 * immediately with status=PENDING and a topicId. A background @Async job calls
 * Groq (with Gemini fallback), validates the JSON, and writes the result to
 * Redis under "insight:async:{topicId}". The client polls the same topicId.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InsightAsyncService {

    private static final String STATUS_KEY_PREFIX = "insight:status:";
    private static final String PAYLOAD_KEY_PREFIX = "insight:async:";
    private static final long STATUS_TTL_MIN = 30;

    private final GroqService groqService;
    private final AiService aiService;        // existing fallback path
    private final InsightSchemaValidator validator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns a deterministic topicId for the given (topic,context) pair so
     * repeated requests for the same insight share a result key.
     */
    public String topicIdFor(String topic, String context) {
        String seed = (topic == null ? "" : topic.toLowerCase(Locale.ROOT))
                + "|" + (context == null ? "" : context);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < digest.length; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(seed.hashCode());
        }
    }

    /**
     * Idempotent kick-off. Returns the current status:
     *   - READY    if a payload is already cached
     *   - PENDING  if generation was already running OR was just started
     */
    public InsightStatusDTO requestInsight(String topic, String context) {
        String topicId = topicIdFor(topic, context);

        // already done?
        Object existing = redisTemplate.opsForValue().get(PAYLOAD_KEY_PREFIX + topicId);
        if (existing instanceof String json) {
            RichInsightDTO dto = validator.parseLenient(json);
            if (dto != null) {
                return InsightStatusDTO.builder()
                        .status("READY")
                        .topicId(topicId)
                        .insight(dto)
                        .message("Cached insight returned")
                        .build();
            }
        }

        // already in flight?
        Object inflight = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + topicId);
        if ("PENDING".equals(inflight)) {
            return InsightStatusDTO.builder()
                    .status("PENDING")
                    .topicId(topicId)
                    .message("Insight generation already in progress")
                    .build();
        }

        // mark + dispatch
        redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + topicId, "PENDING", STATUS_TTL_MIN, TimeUnit.MINUTES);
        generate(topic, context, topicId);
        return InsightStatusDTO.builder()
                .status("PENDING")
                .topicId(topicId)
                .message("Generation started")
                .build();
    }

    /** Read the current status for a topicId. */
    public InsightStatusDTO pollStatus(String topicId) {
        if (topicId == null || topicId.isBlank()) {
            return InsightStatusDTO.builder().status("ERROR").message("topicId required").build();
        }

        Object payload = redisTemplate.opsForValue().get(PAYLOAD_KEY_PREFIX + topicId);
        if (payload instanceof String json) {
            RichInsightDTO dto = validator.parseLenient(json);
            if (dto != null) {
                return InsightStatusDTO.builder()
                        .status("READY")
                        .topicId(topicId)
                        .insight(dto)
                        .build();
            }
        }

        Object status = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + topicId);
        if (status == null) {
            return InsightStatusDTO.builder()
                    .status("ERROR")
                    .topicId(topicId)
                    .message("No insight in flight for this topicId")
                    .build();
        }
        return InsightStatusDTO.builder()
                .status(String.valueOf(status))
                .topicId(topicId)
                .message("Generation in progress")
                .build();
    }

    @Async
    public CompletableFuture<Void> generate(String topic, String context, String topicId) {
        try {
            RichInsightDTO dto = groqService.generateInsight(topic, context);

            // fallback: existing AiService path
            if (dto == null) {
                String fallback = aiService.getMentorExplanation("general market", topic, "observe", "en", "Balanced", Map.of("context", context != null ? context : ""));
                if (fallback != null) {
                    dto = validator.parseLenient(fallback);
                }
            }

            if (dto == null) {
                redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + topicId, "ERROR", STATUS_TTL_MIN, TimeUnit.MINUTES);
                log.warn("Insight generation failed for topic '{}'", topic);
                return CompletableFuture.completedFuture(null);
            }

            String serialized = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(PAYLOAD_KEY_PREFIX + topicId, serialized, 6, TimeUnit.HOURS);
            redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + topicId, "READY", STATUS_TTL_MIN, TimeUnit.MINUTES);
            log.info("Insight ready for topic '{}' (topicId={})", topic, topicId);
        } catch (Exception ex) {
            log.error("Insight generation crashed for '{}': {}", topic, ex.getMessage());
            redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + topicId, "ERROR", STATUS_TTL_MIN, TimeUnit.MINUTES);
        }
        return CompletableFuture.completedFuture(null);
    }
}
