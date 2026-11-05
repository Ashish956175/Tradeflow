package com.tradeflow.api.inventory;

import com.tradeflow.api.product.Product;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

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

    @PostMapping("/stock-out")
    @ResponseStatus(HttpStatus.OK)
    public Product stockOut(
            @Valid @RequestBody StockOutRequest request,
            Authentication authentication
    ) {
        return inventoryService.stockOut(
                request,
                authentication.getName()
        );
    }

    @PostMapping("/adjust")
    @ResponseStatus(HttpStatus.OK)
    public Product adjustStock(
            @Valid @RequestBody StockAdjustmentRequest request,
            Authentication authentication
    ) {
        return inventoryService.adjustStock(
                request,
                authentication.getName()
        );
    }

    @GetMapping("/transactions")
    public Page<InventoryTransactionResponse> getTransactionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) InventoryTransactionType transactionType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate,
            Authentication authentication
    ) {
        return inventoryService.getTransactionHistory(
                page,
                size,
                sortBy,
                direction,
                productId,
                transactionType,
                startDate,
                endDate,
                authentication.getName()
        );
    }
}