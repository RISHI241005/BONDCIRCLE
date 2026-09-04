package com.datingapp.chat.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation conflicts with existing state (e.g. duplicate idempotency key, duplicate block).
 */
public class ConflictException extends BaseException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT, ErrorCode.DUPLICATE_MESSAGE);
    }

    public ConflictException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.CONFLICT, errorCode);
    }
}
