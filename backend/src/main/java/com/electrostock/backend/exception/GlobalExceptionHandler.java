package com.electrostock.backend.exception;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException exception){
		return ResponseEntity.badRequest().body(exception.getMessage());
	}
	@ExceptionHandler(SaleCancellationNotAllowedException.class)
	public ResponseEntity<String> handleSaleCancellationNotAllowed(
	        SaleCancellationNotAllowedException exception) {
	    return ResponseEntity.status(HttpStatus.CONFLICT)
	            .body(exception.getMessage());
	}
}
