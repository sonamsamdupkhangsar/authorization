package me.sonam.auth.service.exception;

public class MaxCountException extends RuntimeException {
    public MaxCountException(String msg) {
        super(msg);
    }
}
