package com.situnes.playlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalid(Exception exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the prompt, target, and exclusion fields.");
        detail.setTitle("Invalid playlist request");
        detail.setProperty("code", "INVALID_REQUEST");
        return detail;
    }

    @ExceptionHandler(PlaylistFailure.class)
    ProblemDetail unavailable(PlaylistFailure exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        detail.setTitle("Suggestions unavailable");
        detail.setProperty("code", exception.code());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        // External exception messages may contain credential-bearing URLs. Log the type only.
        LOG.error("Unhandled playlist error: {}", exception.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "We couldn't generate suggestions. Try again.");
    }
}
