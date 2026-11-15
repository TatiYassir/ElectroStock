package com.electrostock.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

//Add alongside the existing imports
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.beans.factory.annotation.Autowired;

import com.electrostock.backend.model.Currency;
import com.electrostock.backend.repository.CurrencyRepository;

@SpringBootTest
class BackendApplicationTests {
	// Add inside BackendApplicationTests
	@Autowired
	private CurrencyRepository currencyRepository;

	@Test
	void findsSeededMadCurrency() {
	    Currency mad = currencyRepository.findByCode("MAD")
	            .orElseThrow(() -> new AssertionError("MAD was not found in the database"));

	    assertEquals("MAD", mad.getCode());
	}
	@Test
	void contextLoads() {
	}

}
