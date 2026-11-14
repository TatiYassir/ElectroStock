package com.electrostock.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.electrostock.backend.enums.SupplierStatus;
@Entity
@Table(name = "suppliers")
public class Supplier {
	// Attributes :
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "name", nullable = false,
			unique = false,length = 200)
	@Size(max = 200)
	@NotBlank
	private String name;
	@Column(name = "phone",length = 30)
	@Size(max = 30)
	private String phone;
	@Column(name = "email",length = 254)
	@Size(max = 254)
	private String email;
	@Column(columnDefinition = "TEXT")
	private String address;
	@Enumerated(EnumType.STRING)
	@Column(name = "status" , nullable = false, length = 20)
	private SupplierStatus status = SupplierStatus.ACTIVE;
	//NO-ARGUMNET CONSTRUCTOR for JPA:
	protected Supplier() {
		
	}
	//ARGUMENT CONSTRATOR :
	public Supplier(String name,String phone,
			String email,String address) {
		this.name = validateAndNormalizeName(name);
		this.phone = phone;
		this.email = email;
		this.address = address;
	}
	//Getters :
	public Long getId() {
		return id;
	}
	public String getName() {
		return name;
	}
	public String getPhone() {
		return phone;
	}
	public String getEmail() {
		return email;
	}
	public String getAddress() {
		return address;
	}
	public SupplierStatus getStatus() {
		return status;
	}
	//Setters :
	
	public void setName(String name) {
		this.name = validateAndNormalizeName(name);
	}
	public void setPhone(String phone) {
		this.phone = phone;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	
	//method : 
	private String validateAndNormalizeName(String name) {
		if(name == null || name.isBlank()) {
			throw new IllegalArgumentException("Supplier name is required");
		}
		name = name.strip();
		return name;
	}
	public void deactivate() {
		status = SupplierStatus.INACTIVE;
	}
	public void reactivate() {
		status = SupplierStatus.ACTIVE;
	}
}
