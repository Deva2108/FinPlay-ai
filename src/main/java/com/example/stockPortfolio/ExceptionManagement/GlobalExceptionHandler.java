package com.example.stockPortfolio.ExceptionManagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorClass> buildErrorResponse(String message, HttpStatus status) {
        ErrorClass error = new ErrorClass(
                status.value(),
                message,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorClass> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({UserNotFoundException.class, HoldingNotFoundException.class})
    public ResponseEntity<ErrorClass> handleNotFoundException(RuntimeException ex){
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorClass> handleValidationException(MethodArgumentNotValidException ex){
        String errMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        return buildErrorResponse(errMsg, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(StockDataUnavailableException.class)
    public ResponseEntity<ErrorClass> handleStockDataUnavailable(StockDataUnavailableException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorClass> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorClass> handleGeneric(Exception ex) {
        log.error("UNEXPECTED ERROR: ", ex);
        return buildErrorResponse("An unexpected internal error occurred.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
