package com.datingapp.chat.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard unified success response envelope for all REST API endpoints.
 *
 * @param <T> Payload data type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper")
public class ApiResponse<T> {

    @Schema(description = "Indicates whether the request was successful", example = "true")
    private final boolean success;

    @Schema(description = "Response payload data")
    private final T data;

    @Schema(description = "Optional user-facing or status message", example = "Operation completed successfully")
    private final String message;

    @Schema(description = "Timestamp when the response was generated in UTC", example = "2026-08-26T20:16:00.000Z")
    private final Instant timestamp;

    @Schema(description = "Unique correlation request ID", example = "c1f7a064-1065-4f4c-bc69-d4ffca356b62")
    private final String requestId;

    private ApiResponse(boolean success, T data, String message, Instant timestamp, String requestId) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.timestamp = timestamp;
        this.requestId = requestId;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now(), UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> of(boolean success, T data, String message, String requestId) {
        return new ApiResponse<>(success, data, message, Instant.now(), requestId != null ? requestId : UUID.randomUUID().toString());
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getRequestId() {
        return requestId;
    }
}
