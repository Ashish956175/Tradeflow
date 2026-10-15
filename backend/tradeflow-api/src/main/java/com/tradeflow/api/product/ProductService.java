package com.tradeflow.api.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

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

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Product not found"
            ));
    }
}