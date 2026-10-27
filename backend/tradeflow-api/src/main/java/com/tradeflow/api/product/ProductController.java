package com.tradeflow.api.product;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {


    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(
            @Valid @RequestBody ProductRequest request
    ) {
        return productService.createProduct(request);
    }

    @GetMapping public Page<Product> getAllProducts( @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestParam(defaultValue = "id") String sortBy,
                                                     @RequestParam(defaultValue = "asc") String direction,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) BigDecimal minPrice,
                                                     @RequestParam(required = false) BigDecimal maxPrice,
                                                     @RequestParam(required = false) Integer minQuantity )
    {
        return productService.getAllProducts( page, size, sortBy, direction,
                keyword, minPrice, maxPrice, minQuantity
        );
    }

    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }


}
