package com.tradeflow.api.shipment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipments")
@Validated
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    // Create a shipment for an order belonging to the authenticated supplier.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Shipment createShipment(
            @Valid @RequestBody ShipmentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return shipmentService.createShipment(
                request,
                jwt.getSubject()
        );
    }

    // Retrieve all shipments (administrator only).
    @GetMapping
    public List<Shipment> getAllShipments(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return shipmentService.getAllShipments(jwt.getSubject());
    }

    // Retrieve a shipment by ID after checking ownership.
    @GetMapping("/{id}")
    public Shipment getShipmentById(
            @PathVariable
            @Positive(message = "Shipment ID must be positive")
            Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return shipmentService.getShipmentById(
                id,
                jwt.getSubject()
        );
    }

    // Retrieve a shipment by tracking number after checking ownership.
    @GetMapping("/tracking/{trackingNumber}")
    public Shipment getShipmentByTrackingNumber(
            @PathVariable String trackingNumber,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return shipmentService.getShipmentByTrackingNumber(
                trackingNumber,
                jwt.getSubject()
        );
    }

    // Retrieve a shipment for an order the user can access.
    @GetMapping("/order/{purchaseOrderId}")
    public Shipment getShipmentByOrderId(
            @PathVariable
            @Positive(message = "Purchase order ID must be positive")
            Long purchaseOrderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return shipmentService.getShipmentByOrderId(
                purchaseOrderId,
                jwt.getSubject()
        );
    }

    // Filter shipments by status (administrator only).
    @GetMapping("/status/{status}")
    public List<Shipment> getShipmentsByStatus(
            @PathVariable ShipmentStatus status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return shipmentService.getShipmentsByStatus(
                status,
                jwt.getSubject()
        );
    }

    // Update shipment status after checking supplier ownership or admin role.
    @PatchMapping("/{id}/status")
    public Shipment updateShipmentStatus(
            @PathVariable
            @Positive(message = "Shipment ID must be positive")
            Long id,
            @RequestParam ShipmentStatus status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return shipmentService.updateShipmentStatus(
                id,
                status,
                jwt.getSubject()
        );
    }
}