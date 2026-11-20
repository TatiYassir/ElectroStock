package com.electrostock.backend.exception;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.electrostock.backend.exception.category.CategoryAlreadyExistsException;
import com.electrostock.backend.exception.category.CategoryInUseException;
import com.electrostock.backend.exception.category.CategoryNotFoundException;

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
	@ExceptionHandler(PurchaseCancellationNotAllowedException.class)
	public ResponseEntity<String> handlePurchaseCancellationNotAllowed(
	        PurchaseCancellationNotAllowedException exception) {
	    return ResponseEntity.status(HttpStatus.CONFLICT)
	            .body(exception.getMessage());
	}
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<String> handleIllegalState(
	        IllegalStateException exception) {

	    return ResponseEntity.status(HttpStatus.CONFLICT)
	            .body(exception.getMessage());
	}
	@ExceptionHandler(CategoryNotFoundException.class)
	public ResponseEntity<String> handleCategoryNotFound(CategoryNotFoundException e) {
	    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}
	@ExceptionHandler({CategoryAlreadyExistsException.class, CategoryInUseException.class})
	public ResponseEntity<String> handleCategoryConflict(RuntimeException e) {
	    return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
	}
}
