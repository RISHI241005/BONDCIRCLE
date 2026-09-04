package com.datingapp.chat.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when client sends invalid parameters, malformed payload, or violation of limits.
 */
public class BadRequestException extends BaseException {

    public BadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST);
    }

    public BadRequestException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.BAD_REQUEST, errorCode);
    }
}
