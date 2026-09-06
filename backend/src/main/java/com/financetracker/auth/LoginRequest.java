package com.financetracker.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The login body.
 *
 * The email is not checked against an address format on purpose.
 * The lookup is an exact match against a stored address, so a format rule would only add a second way to be told the input was wrong.
 * The length caps are there to keep an oversized body away from the Argon2 comparison.
 */
public record LoginRequest(
        @NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 200) String password) {
}
