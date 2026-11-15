package com.tradeflow.api.order;

import com.tradeflow.api.inventory.InventoryService;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.quotation.Quotation;
import com.tradeflow.api.quotation.QuotationRepository;
import com.tradeflow.api.quotation.QuotationStatus;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRole;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final QuotationRepository quotationRepository;
    private final MembershipAuthorizationService membershipAuthorizationService;
    private final InventoryService inventoryService;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            QuotationRepository quotationRepository,
            MembershipAuthorizationService membershipAuthorizationService,
            InventoryService inventoryService
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.quotationRepository = quotationRepository;
        this.membershipAuthorizationService = membershipAuthorizationService;
        this.inventoryService = inventoryService;
    }

    // A buyer can create an order only from their own accepted quotation.
    @Transactional
    public PurchaseOrder createOrderFromQuotation(
            Long quotationId,
            String deliveryAddress,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        rejectPlatformAdmin(user);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Quotation not found"
                ));

        Long buyerOrganizationId = getBuyerOrganizationId(quotation);

        membershipAuthorizationService.requireOrganizationCapability(
                authenticatedEmail,
                buyerOrganizationId,
                OrganizationCapability.BUYER
        );

        if (quotation.getStatus() != QuotationStatus.ACCEPTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A purchase order can only be created from an accepted quotation"
            );
        }

        if (purchaseOrderRepository.existsByQuotation_Id(quotationId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A purchase order already exists for this quotation"
            );
        }

        ProcurementRequest procurementRequest =
                quotation.getProcurementRequest();

        String finalDeliveryAddress =
                deliveryAddress != null && !deliveryAddress.isBlank()
                        ? deliveryAddress.trim()
                        : procurementRequest.getDeliveryAddress();

        PurchaseOrder purchaseOrder = new PurchaseOrder(
                quotation,
                finalDeliveryAddress
        );

        return purchaseOrderRepository.save(purchaseOrder);
    }

    // Retrieve an order by database ID after checking organization access.
    @Transactional(readOnly = true)
    public PurchaseOrder getOrderById(
            Long id,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        PurchaseOrder order = findOrderById(id);

        verifyOrderReadAccess(user, order, authenticatedEmail);

        return order;
    }

    // Retrieve an order by business order number after checking organization access.
    @Transactional(readOnly = true)
    public PurchaseOrder getOrderByNumber(
            String orderNumber,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        PurchaseOrder order = purchaseOrderRepository
                .findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        verifyOrderReadAccess(user, order, authenticatedEmail);

        return order;
    }

    // Only platform administrators can retrieve every order.
    @Transactional(readOnly = true)
    public List<PurchaseOrder> getAllOrders(String authenticatedEmail) {

        membershipAuthorizationService
                .requirePlatformAdmin(authenticatedEmail);

        return purchaseOrderRepository.findAll();
    }

    // Only platform administrators can filter all orders by status.
    @Transactional(readOnly = true)
    public List<PurchaseOrder> getOrdersByStatus(
            OrderStatus status,
            String authenticatedEmail
    ) {
        membershipAuthorizationService
                .requirePlatformAdmin(authenticatedEmail);

        return purchaseOrderRepository.findByStatus(status);
    }

    // Supplier members with SUPPLIER capability confirm orders and reserve inventory.
    @Transactional
    public PurchaseOrder confirmOrder(
            Long id,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        rejectPlatformAdmin(user);

        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        Long supplierOrganizationId = getSupplierOrganizationId(order);

        membershipAuthorizationService.requireOrganizationCapability(
                authenticatedEmail,
                supplierOrganizationId,
                OrganizationCapability.SUPPLIER
        );

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only CREATED orders can be confirmed"
            );
        }

        if (order.getOrderedQuantity() == null
                || order.getOrderedQuantity() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order quantity must be greater than zero"
            );
        }

        Long productId = order.getQuotation()
                .getProcurementRequest()
                .getProduct()
                .getId();

        inventoryService.reserveForOrder(
                productId,
                order.getOrderedQuantity(),
                user,
                order
        );

        try {
            order.confirm();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        return purchaseOrderRepository.save(order);
    }

    // Supplier members with SUPPLIER capability can mark orders as shipped.
    @Transactional
    public PurchaseOrder shipOrder(
            Long id,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        rejectPlatformAdmin(user);

        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        Long supplierOrganizationId = getSupplierOrganizationId(order);

        membershipAuthorizationService.requireOrganizationCapability(
                authenticatedEmail,
                supplierOrganizationId,
                OrganizationCapability.SUPPLIER
        );

        try {
            order.ship();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        Long productId = order.getQuotation()
                .getProcurementRequest()
                .getProduct()
                .getId();

        inventoryService.recordShipmentForOrder(
                productId,
                order.getOrderedQuantity(),
                user,
                order
        );

        return purchaseOrderRepository.save(order);
    }

    // Buyer members with BUYER capability can mark orders as delivered.
    @Transactional
    public PurchaseOrder deliverOrder(
            Long id,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        rejectPlatformAdmin(user);

        PurchaseOrder order = findOrderById(id);

        Long buyerOrganizationId = getBuyerOrganizationId(order);

        membershipAuthorizationService.requireOrganizationCapability(
                authenticatedEmail,
                buyerOrganizationId,
                OrganizationCapability.BUYER
        );

        try {
            order.deliver();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        return purchaseOrderRepository.save(order);
    }

    // Buyer members with BUYER capability can cancel eligible orders.
    // Cancelling a confirmed order releases its reserved inventory.
    @Transactional
    public PurchaseOrder cancelOrder(
            Long id,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        rejectPlatformAdmin(user);

        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        Long buyerOrganizationId = getBuyerOrganizationId(order);

        membershipAuthorizationService.requireOrganizationCapability(
                authenticatedEmail,
                buyerOrganizationId,
                OrganizationCapability.BUYER
        );

        OrderStatus previousStatus = order.getStatus();

        try {
            order.cancel();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        if (previousStatus == OrderStatus.CONFIRMED) {
            Long productId = order.getQuotation()
                    .getProcurementRequest()
                    .getProduct()
                    .getId();

            inventoryService.releaseForOrder(
                    productId,
                    order.getOrderedQuantity(),
                    user,
                    order
            );
        }

        return purchaseOrderRepository.save(order);
    }

    // Find an order by its database ID.
    private PurchaseOrder findOrderById(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));
    }

    // Platform administrators cannot perform purchase-order mutations.
    private void rejectPlatformAdmin(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform administrators cannot perform this purchase-order operation"
            );
        }
    }

    // Verify that the user can read an order through either organization.
    private void verifyOrderReadAccess(
            User user,
            PurchaseOrder order,
            String authenticatedEmail
    ) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        Long buyerOrganizationId = getBuyerOrganizationId(order);
        Long supplierOrganizationId = getSupplierOrganizationId(order);

        try {
            membershipAuthorizationService.requireOrganizationCapability(
                    authenticatedEmail,
                    buyerOrganizationId,
                    OrganizationCapability.BUYER
            );

            return;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.FORBIDDEN) {
                throw exception;
            }
        }

        try {
            membershipAuthorizationService.requireOrganizationCapability(
                    authenticatedEmail,
                    supplierOrganizationId,
                    OrganizationCapability.SUPPLIER
            );

            return;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.FORBIDDEN) {
                throw exception;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this purchase order"
        );
    }

    // Derive the buyer organization from the quotation's procurement request.
    private Long getBuyerOrganizationId(Quotation quotation) {
        return quotation.getProcurementRequest()
                .getBuyerOrganization()
                .getId();
    }

    // Derive the buyer organization from the purchase order.
    private Long getBuyerOrganizationId(PurchaseOrder order) {
        return getBuyerOrganizationId(order.getQuotation());
    }

    // Derive the supplier organization from the quotation's supplier profile.
    private Long getSupplierOrganizationId(PurchaseOrder order) {
        return order.getQuotation()
                .getSupplier()
                .getOrganization()
                .getId();
    }
}