package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Revise os campos informados",
                request,
                fields
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraint(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                fields.putIfAbsent(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                )
        );

        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Revise os parâmetros informados",
                request,
                fields
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return build(
                status,
                status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "REQUEST_REJECTED",
                exception.getReason() == null ? status.getReasonPhrase() : exception.getReason(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ApiError> sql(SQLException exception, HttpServletRequest request) {
        if ("45000".equals(exception.getSQLState())) {
            return build(
                    HttpStatus.BAD_REQUEST,
                    "BUSINESS_RULE_VIOLATION",
                    safeSqlMessage(exception),
                    request,
                    Map.of()
            );
        }

        if ("23000".equals(exception.getSQLState())) {
            return build(
                    HttpStatus.CONFLICT,
                    "DATA_CONFLICT",
                    "Já existe um registro com um identificador único informado",
                    request,
                    Map.of()
            );
        }

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DATABASE_ERROR",
                "Não foi possível concluir a operação no banco de dados",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_JSON",
                "O corpo da requisição está inválido",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException exception, HttpServletRequest request) {
        return build(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Você não tem permissão para executar esta operação",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Ocorreu um erro interno ao processar a solicitação",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return ResponseEntity.status(status).body(new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        ));
    }

    private String safeSqlMessage(SQLException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Operação recusada por regra de negócio";

        int marker = message.indexOf("MESSAGE_TEXT =");
        if (marker >= 0) return message.substring(marker + "MESSAGE_TEXT =".length()).trim();

        return message.length() > 240 ? message.substring(0, 240) : message;
    }
}
