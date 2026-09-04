package com.datingapp.chat.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated user attempts to access a conversation, message,
 * or action they are not authorized to perform (e.g. Chat access denied, blocked user).
 */
public class ForbiddenException extends BaseException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.FORBIDDEN, errorCode);
    }
}
