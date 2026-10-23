package com.tradeflow.api.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(
            PurchaseOrderService purchaseOrderService
    ) {
        this.purchaseOrderService = purchaseOrderService;
    }

    // Create an order from an accepted quotation belonging to this buyer.
    @PostMapping("/quotation/{quotationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrder createOrderFromQuotation(
            @PathVariable @Positive Long quotationId,
            @RequestParam(required = false)
            @Size(max = 500, message = "Delivery address cannot exceed 500 characters")
            String deliveryAddress,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return purchaseOrderService.createOrderFromQuotation(
                quotationId,
                deliveryAddress,
                jwt.getSubject()
        );
    }

    // Retrieve all orders (administrator only).
    @GetMapping
    public List<PurchaseOrder> getAllOrders(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return purchaseOrderService.getAllOrders(jwt.getSubject());
    }

    // Retrieve an order by database ID after checking ownership.
    @GetMapping("/{id}")
    public PurchaseOrder getOrderById(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return purchaseOrderService.getOrderById(
                id,
                jwt.getSubject()
        );
    }

    // Retrieve an order by business order number after checking ownership.
    @GetMapping("/number/{orderNumber}")
    public PurchaseOrder getOrderByNumber(
            @PathVariable @NotBlank String orderNumber,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return purchaseOrderService.getOrderByNumber(
                orderNumber,
                jwt.getSubject()
        );
    }

    // Filter all orders by status (administrator only).
    @GetMapping("/status/{status}")
    public List<PurchaseOrder> getOrdersByStatus(
            @PathVariable OrderStatus status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return purchaseOrderService.getOrdersByStatus(
                status,
                jwt.getSubject()
        );
    }

    // Confirm an order (administrator only).
    @PatchMapping("/{id}/confirm")
    public PurchaseOrder confirmOrder(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return purchaseOrderService.confirmOrder(
                id,
                jwt.getSubject()
        );
    }

    // Mark an order as shipped (administrator only).
    @PatchMapping("/{id}/ship")
    public PurchaseOrder shipOrder(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return purchaseOrderService.shipOrder(
                id,
                jwt.getSubject()
        );
    }

    // Mark an order as delivered (administrator only).
    @PatchMapping("/{id}/deliver")
    public PurchaseOrder deliverOrder(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return purchaseOrderService.deliverOrder(
                id,
                jwt.getSubject()
        );
    }

    // Cancel an order (administrator only).
    @PatchMapping("/{id}/cancel")
    public PurchaseOrder cancelOrder(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return purchaseOrderService.cancelOrder(
                id,
                jwt.getSubject()
        );
    }
}