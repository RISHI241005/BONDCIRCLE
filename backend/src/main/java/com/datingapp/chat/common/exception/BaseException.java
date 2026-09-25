package com.datingapp.chat.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base unchecked exception for all chat service business and runtime errors.
 */
public abstract class BaseException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final ErrorCode errorCode;

    public BaseException(String message, HttpStatus httpStatus, ErrorCode errorCode) {
        super(message != null ? message : errorCode.getDefaultMessage());
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public BaseException(String message, Throwable cause, HttpStatus httpStatus, ErrorCode errorCode) {
        super(message != null ? message : errorCode.getDefaultMessage(), cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
