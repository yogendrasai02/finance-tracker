package com.financetracker.common.error;

/** One field's constraint failure: the field name and the constraint message, never the value submitted for it (SECURITY.md §2). */
record FieldViolation(String field, String message) {
}
