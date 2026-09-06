package com.financetracker.common.error;

/**
 * Thrown for a row that does not exist, and for a row that exists but belongs to another tenant.
 *
 * Both cases must produce the same response (SR-04, SR-78), so this is the one exception every feature throws for either.
 * The message is for the log, never the client: {@code GlobalExceptionHandler} maps every instance to the same fixed body regardless of what it says.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
