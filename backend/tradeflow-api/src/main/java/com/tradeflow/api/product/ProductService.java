package com.tradeflow.api.product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;


import java.util.Set;

import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "name",
            "sku",
            "unitPrice",
            "availableQuantity",
            "createdAt"
    );

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product createProduct(ProductRequest request) {

        if (productRepository.existsBySku(request.sku())) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "A product with this SKU already exists"
            );
        }

        Product product = new Product();

        product.setName(request.name());
        product.setDescription(request.description());
        product.setSku(request.sku());
        product.setUnitPrice(request.unitPrice());
        product.setAvailableQuantity(request.availableQuantity());
        product.setUnit(request.unit());

        return productRepository.save(product);
    }

    public Page<Product> getAllProducts(
            int page,
            int size,
            String sortBy,
            String direction,
            String keyword,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minQuantity
    ) {

        // Validate pagination
        if (page < 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Page index cannot be negative"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE
            );
        }

        // Validate sorting field
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid sort field. Allowed fields: " + ALLOWED_SORT_FIELDS
            );
        }

        // Validate sorting direction
        Sort.Direction sortDirection;

        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Sort direction must be 'asc' or 'desc'"
            );
        }

        // Validate filter values
        if (minPrice != null && minPrice.signum() < 0)  {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Minimum price cannot be negative"
            );
        }

        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Maximum price cannot be negative"
            );
        }

        if (minPrice != null
                && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Minimum price cannot exceed maximum price"
            );
        }

        if (minQuantity != null && minQuantity < 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Minimum quantity cannot be negative"
            );
        }

        // Build pagination and sorting
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(sortDirection, sortBy)
        );

        // Combine filters dynamically
        Specification<Product> specification =
                Specification.<Product>unrestricted()
                        .and(ProductSpecification.hasKeyword(keyword))
                        .and(ProductSpecification.hasMinPrice(minPrice))
                        .and(ProductSpecification.hasMaxPrice(maxPrice))
                        .and(ProductSpecification.hasMinQuantity(minQuantity));

        return productRepository.findAll(specification, pageable);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Product not found"
                ));
    }
}
