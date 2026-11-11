package com.electrostock.backend.exception;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CategoryAlreadyExistsException extends RuntimeException{
	public CategoryAlreadyExistsException(String message) {
		super(message);
	}
}
