package com.electrostock.backend.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PatchMapping;

import com.electrostock.backend.dto.SupplierRequest;
import com.electrostock.backend.model.Supplier;
import com.electrostock.backend.service.SupplierService;
@RestController
@RequestMapping("/api/suppliers")
public class SupplierRestController {
	//Attribute :
	private final SupplierService supplierService;
	//Constructor
	public SupplierRestController(SupplierService supplierService) {
		this.supplierService = supplierService;
	}
	//Endpoints :
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Supplier createSupplier(@RequestBody SupplierRequest request) {
		return supplierService.createSupplier(request.getName(),request.getPhone(),
				request.getEmail(), request.getAddress());
	}
	@GetMapping
	public List<Supplier> getAllSuppliers(){
		return supplierService.getAllSuppliers();
	}
	@GetMapping("/{id}")
	public Supplier getSupplierById(@PathVariable Long id) {
		return supplierService.getSupplierById(id);
	}
	@PutMapping("/{id}")
	public Supplier updateSuplier(@PathVariable Long id,
			@RequestBody SupplierRequest request) {
		return supplierService.updateSupplier(id, request.getName(),
				request.getPhone(), request.getEmail(), request.getAddress());
	}
	@PatchMapping("/{id}/deactivate")
	public Supplier deactivateSupplier(@PathVariable Long id) {
		return supplierService.deactivateSupplier(id);
	}
	@PatchMapping("/{id}/reactivate")
	public Supplier reactivateSupplier(@PathVariable Long id) {
		return supplierService.reactivateSupplier(id);
	}
}
