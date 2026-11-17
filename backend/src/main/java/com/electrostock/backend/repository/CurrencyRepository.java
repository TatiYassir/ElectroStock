package com.electrostock.backend.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.electrostock.backend.model.Currency;

public interface CurrencyRepository 
		extends JpaRepository <Currency , Long>{
	Optional<Currency>  findByCode(String code);
	List<Currency> findAllByOrderByCodeAsc();
}
