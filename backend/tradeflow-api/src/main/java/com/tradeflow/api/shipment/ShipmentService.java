package com.tradeflow.api.shipment;

import com.tradeflow.api.order.OrderStatus;
import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.order.PurchaseOrderRepository;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserRepository userRepository;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            UserRepository userRepository
    ) {
        this.shipmentRepository = shipmentRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.userRepository = userRepository;
    }

    // Create a shipment for an order belonging to the authenticated supplier.
    @Transactional
    public Shipment createShipment(
            ShipmentRequest request,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.SUPPLIER);

        PurchaseOrder purchaseOrder = purchaseOrderRepository
                .findById(request.purchaseOrderId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        verifySupplierOwnsOrder(user, purchaseOrder);

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

    // Only administrators can retrieve all shipments.
    @Transactional(readOnly = true)
    public List<Shipment> getAllShipments(String authenticatedEmail) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        return shipmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentById(
            Long id,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        Shipment shipment = findShipmentById(id);
        verifyShipmentAccess(user, shipment);

        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentByTrackingNumber(
            String trackingNumber,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        Shipment shipment = shipmentRepository
                .findByTrackingNumber(trackingNumber.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shipment not found"
                ));

        verifyShipmentAccess(user, shipment);

        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentByOrderId(
            Long purchaseOrderId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        PurchaseOrder order = purchaseOrderRepository
                .findById(purchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        verifyOrderAccess(user, order);

        return shipmentRepository
                .findByPurchaseOrder_Id(purchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shipment not found for this purchase order"
                ));
    }

    // Only administrators can query shipments across organizations.
    @Transactional(readOnly = true)
    public List<Shipment> getShipmentsByStatus(
            ShipmentStatus status,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        return shipmentRepository.findByStatus(status);
    }

    // Only the supplier responsible for the order or an administrator
    // can update shipment status.
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

        User user = getAuthenticatedUser(authenticatedEmail);

        Shipment shipment = findShipmentById(shipmentId);

        if (user.getRole() != UserRole.ADMIN) {
            requireRole(user, UserRole.SUPPLIER);
            verifySupplierOwnsOrder(
                    user,
                    shipment.getPurchaseOrder()
            );
        }


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

            shipment.getPurchaseOrder().deliver();
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

    private User getAuthenticatedUser(String email) {
        String normalizedEmail = email.trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        if (!user.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is disabled"
            );
        }

        return user;
    }

    private void requireRole(User user, UserRole requiredRole) {
        if (user.getRole() != requiredRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this action"
            );
        }
    }

    private Organization requireOrganization(User user) {
        Organization organization = user.getOrganization();

        if (organization == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is not associated with an organization"
            );
        }

        return organization;
    }

    private void verifySupplierOwnsOrder(
            User user,
            PurchaseOrder order
    ) {
        Organization organization = requireOrganization(user);

        Long supplierOrganizationId = order.getQuotation()
                .getSupplier()
                .getOrganization()
                .getId();

        if (!organization.getId().equals(supplierOrganizationId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot manage shipments for another supplier's order"
            );
        }
    }

    private void verifyOrderAccess(
            User user,
            PurchaseOrder order
    ) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        Organization organization = requireOrganization(user);

        if (user.getRole() == UserRole.BUYER) {
            Long buyerOrganizationId = order.getQuotation()
                    .getProcurementRequest()
                    .getBuyerOrganization()
                    .getId();

            if (organization.getId().equals(buyerOrganizationId)) {
                return;
            }
        }

        if (user.getRole() == UserRole.SUPPLIER) {
            Long supplierOrganizationId = order.getQuotation()
                    .getSupplier()
                    .getOrganization()
                    .getId();

            if (organization.getId().equals(supplierOrganizationId)) {
                return;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this order"
        );
    }

    private void verifyShipmentAccess(
            User user,
            Shipment shipment
    ) {
        verifyOrderAccess(user, shipment.getPurchaseOrder());
    }
}