package com.electrostock.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
@Entity
@Table (name = "categories")
public class Category {
	// Attributes :
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "name", nullable = false,
			unique = true, length = 100)
	private String name;
	@Column(columnDefinition = "TEXT")
	private String description;
	
	// NO-ARGUMENT CONSTRUCTOR
	protected Category() {

	}
	// ARGUMENT CONSTRUCTOR :
	public Category(String name, String description) {
		this.name = name;
		this.description = description;
	}
	// GETTERS AND SETTERS :
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
	public Long getId() {
		return id;
	}
	
}
 