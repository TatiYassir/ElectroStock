package com.electrostock.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "currencies")
public class Currency {
	//Attributes :
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "code", nullable = false, unique = true, length = 3)
	@NotBlank
	@Size(max = 3)
	private String code;
	@Column(name = "name", nullable = false, length = 100)
	@NotBlank
	@Size(max = 100)
	private String name;
	@Column(name = "symbol" , length = 10)
	@Size(max = 10)
	private String symbol;
	//NO-ARGUMNET CONSTRUCTOR for JPA :
	protected Currency () {
		
	}
	//Getters :
	public Long getId() {
		return id;
	}
	public String getCode() {
		return code;
	}
	public String getName() {
		return name;
	}
	public String getSymbol() {
		return symbol;
	}
	
}
