package com.datingapp.chat.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource (Conversation, Message, Participant) cannot be found.
 */
public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND);
    }

    public ResourceNotFoundException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.NOT_FOUND, errorCode);
    }
}
