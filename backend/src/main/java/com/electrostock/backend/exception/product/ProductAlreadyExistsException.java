package com.electrostock.backend.exception.product;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ProductAlreadyExistsException 
		extends RuntimeException{
	public ProductAlreadyExistsException(String message) {
		super(message);
	}
}
