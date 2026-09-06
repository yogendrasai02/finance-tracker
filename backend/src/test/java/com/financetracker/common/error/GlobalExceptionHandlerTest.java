package com.financetracker.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/** Plain unit tests, no Spring context: only the mapping from exception to response body. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnTheSameNotFoundBodyRegardlessOfTheExceptionMessage() {
        ProblemDetail missing = handler.handleNotFound(new NotFoundException("account 5 does not exist"));
        ProblemDetail notYours = handler.handleNotFound(new NotFoundException("account 5 belongs to another tenant"));

        assertThat(missing.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(missing).isEqualTo(notYours);
        assertThat(missing.getDetail()).doesNotContain("account 5");
    }

    @Test
    void shouldReturnFieldNamesButNeverTheRejectedValue() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "loginRequest");
        bindingResult.addError(new FieldError(
                "loginRequest", "password", "not-the-real-password", false, null, null, "must not be blank"));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyValidatedMethod", String.class), 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ProblemDetail problem = handler.handleValidationFailure(exception);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.toString()).contains("password").contains("must not be blank");
        assertThat(problem.toString()).doesNotContain("not-the-real-password");
    }

    /** Exists only so the test above has a real {@link java.lang.reflect.Method} to build a {@link MethodParameter} from. */
    @SuppressWarnings("unused")
    private static void dummyValidatedMethod(String password) {
    }
}
