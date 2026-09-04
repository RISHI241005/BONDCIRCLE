package com.datingapp.chat.common.exception;

import com.datingapp.chat.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Global REST Controller Advice intercepting all exceptions and formatting
 * them into the unified ApiError response contract.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiError> handleBaseException(BaseException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.warn("Application exception [{}]: {} (Request: {} {})",
                ex.getErrorCode().getCode(), ex.getMessage(), request.getMethod(), request.getRequestURI());

        ApiError error = new ApiError(
                ex.getMessage(),
                ex.getErrorCode().getCode(),
                null,
                requestId
        );
        return new ResponseEntity<>(error, ex.getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        List<ApiError.ValidationErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(new ApiError.ValidationErrorDetail(
                    fieldError.getField(),
                    fieldError.getRejectedValue(),
                    fieldError.getDefaultMessage()
            ));
        }

        log.warn("Validation failed for request: {} {} ({} field errors)",
                request.getMethod(), request.getRequestURI(), details.size());

        ApiError error = new ApiError(
                "Input validation failed",
                ErrorCode.VALIDATION_FAILED.getCode(),
                details,
                requestId
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        List<ApiError.ValidationErrorDetail> details = new ArrayList<>();
        ex.getConstraintViolations().forEach(cv -> details.add(new ApiError.ValidationErrorDetail(
                cv.getPropertyPath().toString(),
                cv.getInvalidValue(),
                cv.getMessage()
        )));

        log.warn("Constraint violation for request: {} {}", request.getMethod(), request.getRequestURI());

        ApiError error = new ApiError(
                "Constraint validation failed",
                ErrorCode.VALIDATION_FAILED.getCode(),
                details,
                requestId
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.warn("Malformed JSON request: {} {}", request.getMethod(), request.getRequestURI());

        ApiError error = new ApiError(
                "Malformed JSON request body",
                ErrorCode.BAD_REQUEST.getCode(),
                null,
                requestId
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleTypeMismatch(Exception ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.warn("Invalid request parameters: {} {}", request.getMethod(), request.getRequestURI());

        ApiError error = new ApiError(
                "Invalid parameter format: " + ex.getMessage(),
                ErrorCode.BAD_REQUEST.getCode(),
                null,
                requestId
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.warn("HTTP method not supported: {} {}", request.getMethod(), request.getRequestURI());

        ApiError error = new ApiError(
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint",
                ErrorCode.BAD_REQUEST.getCode(),
                null,
                requestId
        );
        return new ResponseEntity<>(error, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.warn("Access denied for request: {} {}", request.getMethod(), request.getRequestURI());

        ApiError error = new ApiError(
                "Access denied: You do not have permission to access this resource",
                ErrorCode.FORBIDDEN.getCode(),
                null,
                requestId
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.warn("Authentication failed for request: {} {}", request.getMethod(), request.getRequestURI());

        ApiError error = new ApiError(
                "Authentication required: " + ex.getMessage(),
                ErrorCode.UNAUTHORIZED.getCode(),
                null,
                requestId
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.warn("No handler/resource found: {} {}", request.getMethod(), request.getRequestURI());

        ApiError error = new ApiError(
                "Resource not found: " + request.getRequestURI(),
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                null,
                requestId
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.error("Unhandled server error processing request [{}]: {} {}",
                requestId, request.getMethod(), request.getRequestURI(), ex);

        ApiError error = new ApiError(
                "An unexpected internal error occurred. Reference ID: " + requestId,
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                null,
                requestId
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String reqId = request.getHeader("X-Request-Id");
        return (reqId != null && !reqId.isBlank()) ? reqId : UUID.randomUUID().toString();
    }
}
