package com.example.stockPortfolio.HoldingsManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private Meta meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private String status;
        private String message;
        private String source;
        private java.time.LocalDateTime lastUpdated;
        private String freshness; // "fresh" or "stale"
        private boolean isDelayed;
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ok(data, message, "live");
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null, "Success", "live");
    }

    public static <T> ApiResponse<T> ok(T data, String message, String source) {
        return build(true, data, message, "OK", source);
    }

    public static <T> ApiResponse<T> ok(T data, Meta meta) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(meta.getMessage())
                .meta(meta)
                .build();
    }

    public static <T> ApiResponse<T> syncing(T data, String message) {
        return syncing(data, message, "fallback");
    }

    public static <T> ApiResponse<T> syncing(T data, String message, String source) {
        return build(true, data, message, "SYNCING", source);
    }

    public static <T> ApiResponse<T> error(String message) {
        return build(false, null, message, "ERROR", "fallback");
    }

    private static <T> ApiResponse<T> build(boolean success, T data, String message, String status, String source) {
        return ApiResponse.<T>builder()
                .success(success)
                .data(data)
                .message(message)
                .meta(Meta.builder()
                        .status(status)
                        .message(message)
                        .source(source)
                        .lastUpdated(java.time.LocalDateTime.now())
                        .build())
                .build();
    }
}
