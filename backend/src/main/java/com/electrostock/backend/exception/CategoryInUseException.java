package com.electrostock.backend.exception;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CategoryInUseException 
		extends RuntimeException{
	public CategoryInUseException(String message) {
		super(message);
	}
}
