package it.tesis.weatherbench.controller;

import java.util.Date;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import it.tesis.weatherbench.dto.rest.ExceptionResponse;
import it.tesis.weatherbench.exception.BenchmarkAlreadyRunningException;
import it.tesis.weatherbench.exception.ExternalServiceException;
import it.tesis.weatherbench.exception.WrongInputParameterException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Class to catch exception thrown in the rest controller. All method returns a
 * ResponseEntity<ExceptionResponse>
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ExceptionHandlingController {

    // Generic exception handler
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> defaultErrorHandler(Exception e) {
        log.error("Error request - {}", ExceptionUtils.getStackTrace(e));
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    // Exception for failed validation of query/path parameters
    @ExceptionHandler(WrongInputParameterException.class)
    public ResponseEntity<ExceptionResponse> handleWrongInputParameter(WrongInputParameterException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // Exception for type conversion errors (e.g. unknown {engine} or write mode)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ExceptionResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Throwable rootCause = ExceptionUtils.getRootCause(e);
        String details = rootCause instanceof WrongInputParameterException
                ? rootCause.getMessage()
                : String.format("Parameter '%s' has invalid value '%s'", e.getName(), e.getValue());
        return build(HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ExceptionResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // Exception for Bean Validation constraints on controller method parameters
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ExceptionResponse> handleMethodValidation(HandlerMethodValidationException e) {
        String details = e.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ExceptionResponse> handleConstraintViolation(ConstraintViolationException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(BenchmarkAlreadyRunningException.class)
    public ResponseEntity<ExceptionResponse> handleBenchmarkAlreadyRunning(BenchmarkAlreadyRunningException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    // Duplicate primary key (e.g. same Open-Meteo window ingested twice on JPA)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Data integrity violation - {}", ExceptionUtils.getRootCauseMessage(e));
        return build(HttpStatus.CONFLICT, "Data integrity violation: " + ExceptionUtils.getRootCauseMessage(e));
    }

    // Database not reachable (service not started, wrong MYSQL_PORT / MONGO_PORT)
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ExceptionResponse> handleDatabaseUnavailable(Exception e) {
        log.error("Database unavailable - {}", ExceptionUtils.getRootCauseMessage(e));
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable: " + ExceptionUtils.getRootCauseMessage(e));
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ExceptionResponse> handleExternalService(ExternalServiceException e) {
        log.error("External service error - {}", e.getMessage());
        return build(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    private static ResponseEntity<ExceptionResponse> build(HttpStatus status, String details) {
        return new ResponseEntity<>(new ExceptionResponse(new Date(), details, status.getReasonPhrase(), status.value()), status);
    }
}
