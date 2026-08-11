package dev.jonathan.ledgercore.controller;

import dev.jonathan.ledgercore.service.IdempotencyConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleIllegalArgument(IllegalArgumentException ex) {
        return new ApiError("INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleIdempotencyConflict(IdempotencyConflictException ex) {
        return new ApiError("IDEMPOTENCY_CONFLICT", ex.getMessage());
    }

}
