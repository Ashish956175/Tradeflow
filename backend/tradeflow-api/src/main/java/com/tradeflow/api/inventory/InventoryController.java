package com.tradeflow.api.inventory;

import com.tradeflow.api.product.Product;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/stock-in")
    @ResponseStatus(HttpStatus.OK)
    public Product stockIn(
            @Valid @RequestBody StockInRequest request,
            Authentication authentication
    ) {
        return inventoryService.stockIn(
                request,
                authentication.getName()
        );
    }
}