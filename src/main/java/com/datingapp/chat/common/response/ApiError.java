package com.datingapp.chat.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Standard unified error response envelope for all REST API error scenarios.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error response wrapper")
public class ApiError {

    @Schema(description = "Indicates whether the request was successful", example = "false")
    private final boolean success;

    @Schema(description = "Data payload (null on error)")
    private final Object data;

    @Schema(description = "Human-readable error description", example = "The requested resource was not found")
    private final String message;

    @Schema(description = "Machine-readable standardized error code", example = "RESOURCE_NOT_FOUND")
    private final String errorCode;

    @Schema(description = "Detailed field validation errors where applicable")
    private final List<ValidationErrorDetail> errors;

    @Schema(description = "Timestamp when the error occurred in UTC", example = "2026-08-26T20:16:00.000Z")
    private final Instant timestamp;

    @Schema(description = "Unique correlation request ID", example = "c1f7a064-1065-4f4c-bc69-d4ffca356b62")
    private final String requestId;

    public ApiError(String message, String errorCode, List<ValidationErrorDetail> errors, String requestId) {
        this.success = false;
        this.data = null;
        this.message = message;
        this.errorCode = errorCode;
        this.errors = errors;
        this.timestamp = Instant.now();
        this.requestId = requestId != null ? requestId : UUID.randomUUID().toString();
    }

    public static ApiError of(String message, String errorCode) {
        return new ApiError(message, errorCode, null, UUID.randomUUID().toString());
    }

    public static ApiError of(String message, String errorCode, List<ValidationErrorDetail> errors) {
        return new ApiError(message, errorCode, errors, UUID.randomUUID().toString());
    }

    public static ApiError of(String message, String errorCode, String requestId) {
        return new ApiError(message, errorCode, null, requestId);
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<ValidationErrorDetail> getErrors() {
        return errors;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidationErrorDetail {
        private final String field;
        private final Object rejectedValue;
        private final String message;

        public ValidationErrorDetail(String field, Object rejectedValue, String message) {
            this.field = field;
            this.rejectedValue = rejectedValue;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public Object getRejectedValue() {
            return rejectedValue;
        }

        public String getMessage() {
            return message;
        }
    }
}
