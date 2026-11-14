package com.electrostock.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.electrostock.backend.exception.SupplierNotFoundException;
import com.electrostock.backend.model.Supplier;
import com.electrostock.backend.repository.SupplierRepository;

@Service
public class SupplierService {
	//Attribute :
	private final SupplierRepository supplierRepository;
	//Constructor :
	public SupplierService(SupplierRepository supplierRepository) {
		this.supplierRepository = supplierRepository;
	}
	//Methods :
	public Supplier createSupplier(String name,String phone,
			String email,String address) {
		Supplier supplier = new Supplier(name,phone,email,address);
		return supplierRepository.save(supplier);
	}
	//get a List of suppliers:
	public List<Supplier> getAllSuppliers(){
		return supplierRepository.findAll();
	}
	//get a supplier by his ID:
	public Supplier getSupplierById(Long id) {
		return supplierRepository.findById(id).
				orElseThrow(() -> new SupplierNotFoundException("Supplier not found"));
	}
	//update the supplier:
	public Supplier updateSupplier(Long Id,String name,String phone,
			String email,String address) {
		Supplier supplier = getSupplierById(Id);
		supplier.setName(name);
		supplier.setPhone(phone);
		supplier.setEmail(email);
		supplier.setAddress(address);
		return supplierRepository.save(supplier);
	}
	//Deactivate Supplier :
	public Supplier deactivateSupplier(Long id) {
		Supplier supplier = getSupplierById(id);
		supplier.deactivate();
		return supplierRepository.save(supplier);
	}
	//Reactivate Supplier :
	public Supplier reactivateSupplier(Long id) {
		Supplier supplier = getSupplierById(id);
		supplier.reactivate();
		return supplierRepository.save(supplier);
	}
	
}
