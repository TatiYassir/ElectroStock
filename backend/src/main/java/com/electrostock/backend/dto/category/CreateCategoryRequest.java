package com.electrostock.backend.dto.category;

public class CreateCategoryRequest {
	//ATTRIBUTES :
	private String name;
	private String description;
	// GETTERS & SETTERS
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	
}