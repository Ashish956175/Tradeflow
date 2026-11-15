package com.tradeflow.api.shipment;

import com.tradeflow.api.order.OrderStatus;
import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.order.PurchaseOrderRepository;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRole;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final MembershipAuthorizationService membershipAuthorizationService;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            MembershipAuthorizationService membershipAuthorizationService
    ) {
        this.shipmentRepository = shipmentRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.membershipAuthorizationService = membershipAuthorizationService;
    }

    // Create a shipment for an order belonging to the authenticated supplier.
    @Transactional
    public Shipment createShipment(
            ShipmentRequest request,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform administrators cannot create shipments"
            );
        }

        PurchaseOrder purchaseOrder = purchaseOrderRepository
                .findById(request.purchaseOrderId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        Long supplierOrganizationId = purchaseOrder.getQuotation()
                .getSupplier()
                .getOrganization()
                .getId();

        membershipAuthorizationService.requireOrganizationCapability(
                authenticatedEmail,
                supplierOrganizationId,
                OrganizationCapability.SUPPLIER
        );

        if (purchaseOrder.getStatus() != OrderStatus.SHIPPED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A shipment can only be created for a shipped purchase order"
            );
        }

        if (shipmentRepository.existsByPurchaseOrder_Id(
                purchaseOrder.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A shipment already exists for this purchase order"
            );
        }

        String trackingNumber = request.trackingNumber().trim();

        if (shipmentRepository.existsByTrackingNumber(trackingNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This tracking number is already registered"
            );
        }

        Shipment shipment = new Shipment(
                purchaseOrder,
                request.carrierName().trim(),
                trackingNumber,
                request.deliveryAddress().trim(),
                request.estimatedDeliveryDate()
        );

        return shipmentRepository.save(shipment);
    }

    // Only Platform ADMIN can retrieve all shipments.
    @Transactional(readOnly = true)
    public List<Shipment> getAllShipments(String authenticatedEmail) {
        membershipAuthorizationService.requirePlatformAdmin(
                authenticatedEmail
        );

        return shipmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentById(
            Long id,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        Shipment shipment = findShipmentById(id);

        verifyShipmentAccess(user, authenticatedEmail, shipment);

        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentByTrackingNumber(
            String trackingNumber,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        Shipment shipment = shipmentRepository
                .findByTrackingNumber(trackingNumber.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shipment not found"
                ));

        verifyShipmentAccess(user, authenticatedEmail, shipment);

        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentByOrderId(
            Long purchaseOrderId,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        PurchaseOrder order = purchaseOrderRepository
                .findById(purchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        verifyOrderAccess(user, authenticatedEmail, order);

        return shipmentRepository
                .findByPurchaseOrder_Id(purchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shipment not found for this purchase order"
                ));
    }

    // Only Platform ADMIN can query shipments across organizations.
    @Transactional(readOnly = true)
    public List<Shipment> getShipmentsByStatus(
            ShipmentStatus status,
            String authenticatedEmail
    ) {
        membershipAuthorizationService.requirePlatformAdmin(
                authenticatedEmail
        );

        return shipmentRepository.findByStatus(status);
    }

    // Only the supplier with SUPPLIER capability can update shipment status.
    // Platform ADMIN is read-only for shipment operations.
    @Transactional
    public Shipment updateShipmentStatus(
            Long shipmentId,
            ShipmentStatus newStatus,
            String authenticatedEmail
    ) {
        if (newStatus == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Shipment status cannot be null"
            );
        }

        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform administrators cannot update shipment status"
            );
        }

        Shipment shipment = findShipmentById(shipmentId);

        Long supplierOrganizationId = shipment.getPurchaseOrder()
                .getQuotation()
                .getSupplier()
                .getOrganization()
                .getId();

        membershipAuthorizationService.requireOrganizationCapability(
                authenticatedEmail,
                supplierOrganizationId,
                OrganizationCapability.SUPPLIER
        );

        ShipmentStatus currentStatus = shipment.getStatus();

        boolean validTransition = switch (currentStatus) {
            case CREATED -> newStatus == ShipmentStatus.PICKED_UP
                    || newStatus == ShipmentStatus.FAILED_DELIVERY;

            case PICKED_UP -> newStatus == ShipmentStatus.IN_TRANSIT
                    || newStatus == ShipmentStatus.FAILED_DELIVERY;

            case IN_TRANSIT -> newStatus == ShipmentStatus.OUT_FOR_DELIVERY
                    || newStatus == ShipmentStatus.FAILED_DELIVERY;

            case OUT_FOR_DELIVERY -> newStatus == ShipmentStatus.DELIVERED
                    || newStatus == ShipmentStatus.FAILED_DELIVERY;

            case FAILED_DELIVERY -> newStatus == ShipmentStatus.IN_TRANSIT
                    || newStatus == ShipmentStatus.RETURNED;

            case DELIVERED, RETURNED -> false;
        };

        if (!validTransition) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid shipment status transition from "
                            + currentStatus + " to " + newStatus
            );
        }

        shipment.setStatus(newStatus);

        if (newStatus == ShipmentStatus.DELIVERED) {
            shipment.setActualDeliveryDate(LocalDate.now());
        }

        return shipmentRepository.save(shipment);
    }

    private Shipment findShipmentById(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shipment not found"
                ));
    }

    private void verifyOrderAccess(
            User user,
            String authenticatedEmail,
            PurchaseOrder order
    ) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        Long buyerOrganizationId = order.getQuotation()
                .getProcurementRequest()
                .getBuyerOrganization()
                .getId();

        Long supplierOrganizationId = order.getQuotation()
                .getSupplier()
                .getOrganization()
                .getId();

        boolean hasBuyerAccess = hasOrganizationCapability(
                authenticatedEmail,
                buyerOrganizationId,
                OrganizationCapability.BUYER
        );

        if (hasBuyerAccess) {
            return;
        }

        boolean hasSupplierAccess = hasOrganizationCapability(
                authenticatedEmail,
                supplierOrganizationId,
                OrganizationCapability.SUPPLIER
        );

        if (hasSupplierAccess) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this order"
        );
    }

    private boolean hasOrganizationCapability(
            String authenticatedEmail,
            Long organizationId,
            OrganizationCapability capability
    ) {
        try {
            membershipAuthorizationService.requireOrganizationCapability(
                    authenticatedEmail,
                    organizationId,
                    capability
            );

            return true;

        } catch (ResponseStatusException exception) {

            if (exception.getStatusCode().value()
                    == HttpStatus.FORBIDDEN.value()) {
                return false;
            }

            throw exception;
        }
    }

    private void verifyShipmentAccess(
            User user,
            String authenticatedEmail,
            Shipment shipment
    ) {
        verifyOrderAccess(
                user,
                authenticatedEmail,
                shipment.getPurchaseOrder()
        );
    }
}
