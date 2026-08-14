package dev.jonathan.ledgercore.controller;

import dev.jonathan.ledgercore.service.IdempotencyConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;


@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleIllegalArgument(IllegalArgumentException ex) {
        return new ApiError("INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidationFailure(MethodArgumentNotValidException ex) {
        // Field order from the binding result is not guaranteed; sort so the
        // message is deterministic for a given set of violations.
        List<String> problems = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            problems.add(error.getField() + ": " + error.getDefaultMessage());
        }
        problems.sort(String::compareTo);
        return new ApiError("INVALID_REQUEST", String.join("; ", problems));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMissingHeader(MissingRequestHeaderException ex) {
        return new ApiError("INVALID_REQUEST", "Required header '" + ex.getHeaderName() + "' is missing");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleIdempotencyConflict(IdempotencyConflictException ex) {
        return new ApiError("IDEMPOTENCY_CONFLICT", ex.getMessage());
    }

}
