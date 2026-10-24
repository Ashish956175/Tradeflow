package com.tradeflow.api.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private ProductRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new ProductRequest(
            "Wireless Mouse",
            "Wireless mouse with USB receiver",
            "TF-MOUSE-001",
            new BigDecimal("799.00"),
            50,
            "piece"
        );
    }

    // Test 1: Create a valid product
    @Test
    void createProduct_shouldSaveAndReturnProduct() {

        when(productRepository.existsBySku("TF-MOUSE-001"))
            .thenReturn(false);

        when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> {
                Product product = invocation.getArgument(0);
                return product;
            });

        Product result = productService.createProduct(validRequest);

        assertNotNull(result);
        assertEquals("Wireless Mouse", result.getName());
        assertEquals("TF-MOUSE-001", result.getSku());
        assertEquals(new BigDecimal("799.00"), result.getUnitPrice());
        assertEquals(50, result.getAvailableQuantity());
        assertEquals("piece", result.getUnit());

        verify(productRepository).existsBySku("TF-MOUSE-001");
        verify(productRepository).save(any(Product.class));
    }

    // Test 2: Reject a product with a duplicate SKU
    @Test
    void createProduct_shouldThrowConflictWhenSkuAlreadyExists() {

        when(productRepository.existsBySku("TF-MOUSE-001"))
            .thenReturn(true);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> productService.createProduct(validRequest)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(
            "A product with this SKU already exists",
            exception.getReason()
        );

        verify(productRepository).existsBySku("TF-MOUSE-001");
        verify(productRepository, never()).save(any(Product.class));
    }

    // Test 3: Retrieve an existing product
    @Test
    void getProductById_shouldReturnProductWhenFound() {

        Product product = new Product();
        product.setName("Wireless Mouse");
        product.setDescription("Wireless mouse with USB receiver");
        product.setSku("TF-MOUSE-001");
        product.setUnitPrice(new BigDecimal("799.00"));
        product.setAvailableQuantity(50);
        product.setUnit("piece");

        when(productRepository.findById(1L))
            .thenReturn(Optional.of(product));

        Product result = productService.getProductById(1L);

        assertNotNull(result);
        assertEquals("Wireless Mouse", result.getName());
        assertEquals("TF-MOUSE-001", result.getSku());

        verify(productRepository).findById(1L);
    }

    // Test 4: Return NOT_FOUND when the product does not exist
    @Test
    void getProductById_shouldThrowNotFoundWhenProductDoesNotExist() {

        when(productRepository.findById(999L))
            .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> productService.getProductById(999L)
        );

        assertEquals(404, exception.getStatusCode().value());
        assertEquals("Product not found", exception.getReason());

        verify(productRepository).findById(999L);
    }

    // Bonus test: Return all products
    @Test
    void getAllProducts_shouldReturnAllProducts() {

        Product first = new Product();
        first.setName("Wireless Mouse");
        first.setSku("TF-MOUSE-001");

        Product second = new Product();
        second.setName("Mechanical Keyboard");
        second.setSku("TF-KEYBOARD-001");

        when(productRepository.findAll())
            .thenReturn(List.of(first, second));

        List<Product> result = productService.getAllProducts();

        assertEquals(2, result.size());
        assertEquals("TF-MOUSE-001", result.get(0).getSku());
        assertEquals("TF-KEYBOARD-001", result.get(1).getSku());

        verify(productRepository).findAll();
    }

    @Test
    void createProduct_shouldReturnProductSavedByRepository() {

        when(productRepository.existsBySku("TF-MOUSE-001"))
                .thenReturn(false);

        Product savedProduct = new Product();
        savedProduct.setName("Wireless Mouse");
        savedProduct.setDescription("Wireless mouse with USB receiver");
        savedProduct.setSku("TF-MOUSE-001");
        savedProduct.setUnitPrice(new BigDecimal("799.00"));
        savedProduct.setAvailableQuantity(50);
        savedProduct.setUnit("piece");

        when(productRepository.save(any(Product.class)))
                .thenReturn(savedProduct);

        Product result = productService.createProduct(validRequest);

        assertSame(savedProduct, result);
        assertEquals("Wireless Mouse", result.getName());
        assertEquals("TF-MOUSE-001", result.getSku());

        verify(productRepository).save(any(Product.class));
    }

}