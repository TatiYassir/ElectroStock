package com.electrostock.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

import com.electrostock.backend.enums.ProductStatus;

@Entity
@Table(name ="products")
public class Product {
	// ATTRIBUTES :
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(name = "sku" , nullable = false , unique = true, length = 80)
	@Size(max = 80)
	@NotBlank
	private String sku;
	@Column (name = "name" , nullable = false, length = 200)
	@Size(max = 200)
	@NotBlank
	private String name;
	@Column(columnDefinition = "TEXT")
	private String description;
	@Column(name = "tracks_serials", nullable = false)
	private boolean tracksSerials;
	@Column(name = "selling_price_mad",
	        nullable = false, precision = 19, scale = 4)
	private BigDecimal sellingPriceMad = BigDecimal.ZERO;

	@Column(name = "average_cost_mad",
	        precision = 19, scale = 6)
	private BigDecimal averageCostMad;
	@Column(name = "low_stock_threshold" , nullable = false)
	@Min(0)
	private int lowStockThreshold;
	@Column(name = "warranty_months", nullable = false)
	@Min(0)
	private int warrantyMonths;
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ProductStatus status = ProductStatus.ACTIVE;

	//NO-AUGMENT CONSTRACTOR FOR JPA :
	protected Product () {

	}

	//AUGMENT CONSTRATOR
	public Product(Category category,String sku,String name,boolean tracksSerials,
			String description ,BigDecimal sellingPriceMad) {
		if (category == null) {
			throw new IllegalArgumentException("Category cannot be null.");
		}
		name =validateAndNormalizeName(name);
		sku = validateAndNormalizeSku(sku);
		validateSellingPrice(sellingPriceMad);
		this.category = category;
		this.description = description;
		this.name = name;
		this.sku = sku;
		this.tracksSerials = tracksSerials;
		this.sellingPriceMad = sellingPriceMad;
	}

	// GETTERS :
	public Long getId() {
		return id;
	}
	public Category getCategory() {
		return category;
	}

	public String getSku() {
		return sku;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isTracksSerials() {
		return tracksSerials;
	}

	public BigDecimal getSellingPriceMad() {
		return sellingPriceMad;
	}

	public BigDecimal getAverageCostMad() {
		return averageCostMad;
	}

	public int getLowStockThreshold() {
		return lowStockThreshold;
	}

	public int getWarrantyMonths() {
		return warrantyMonths;
	}

	public ProductStatus getStatus() {
		return status;
	}
	//SETTERS :

	public void setCategory(Category category) {
		if (category == null) {
			throw new IllegalArgumentException("Category cannot be null.");
		}
		this.category = category;
	}

	public void setSku(String sku) {
		this.sku = validateAndNormalizeSku(sku);
	}

	public void setName(String name) {
		this.name = validateAndNormalizeName(name);
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setSellingPriceMad(BigDecimal sellingPriceMad) {
		validateSellingPrice(sellingPriceMad);
		this.sellingPriceMad = sellingPriceMad;
	}

	public void setLowStockThreshold(int lowStockThreshold) {
		if (lowStockThreshold < 0) {
			throw new IllegalArgumentException("lowStockThreshold cannot be negative.");
		}
		this.lowStockThreshold = lowStockThreshold;
	}
	public void setWarrantyMonths(int warrantyMonths) {
		if (warrantyMonths < 0) {
			throw new IllegalArgumentException("warrantyMonths cannot be negative.");
		}
	    this.warrantyMonths = warrantyMonths;
	}

	//Methods :
	private void validateSellingPrice(BigDecimal price) {
		if(price == null ||
				price.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException(
					"Product selling price cannot be null or negative");
		}
	}
	private String validateAndNormalizeSku(String sku) {
		if(sku == null || sku.isBlank()) {
			throw new IllegalArgumentException("Product SKU cannot be blank");
		}
		sku = sku.strip();
		if(sku.length() > 80) {
			throw new IllegalArgumentException(
					"Product SKU cannot exceed 80 characters");
		}
		return sku;
	}
	private String validateAndNormalizeName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Product name cannot be blank");
		}
		name = name.strip();
		if(name.length() > 200) {
			throw new IllegalArgumentException(""
					+ "Product name cannot exceed 200 characters ");
		}
		return name;
	}
}