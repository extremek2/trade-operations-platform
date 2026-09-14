package com.tradeoperationsplatform.apiserver.global.exception;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
