package com.tradeflow.api.order;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.quotation.Quotation;
import com.tradeflow.api.quotation.QuotationRepository;
import com.tradeflow.api.quotation.QuotationStatus;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final QuotationRepository quotationRepository;
    private final UserRepository userRepository;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            QuotationRepository quotationRepository,
            UserRepository userRepository
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.quotationRepository = quotationRepository;
        this.userRepository = userRepository;
    }

    // A buyer can create an order only from their own accepted quotation.
    @Transactional
    public PurchaseOrder createOrderFromQuotation(
            Long quotationId,
            String deliveryAddress,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.BUYER);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Quotation not found"
                ));

        verifyBuyerOwnsQuotation(user, quotation);

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

    // Retrieve an order by database ID after checking ownership.
    @Transactional(readOnly = true)
    public PurchaseOrder getOrderById(
            Long id,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        PurchaseOrder order = findOrderById(id);

        verifyOrderAccess(user, order);

        return order;
    }

    // Retrieve an order by business order number after checking ownership.
    @Transactional(readOnly = true)
    public PurchaseOrder getOrderByNumber(
            String orderNumber,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        PurchaseOrder order = purchaseOrderRepository
                .findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        verifyOrderAccess(user, order);

        return order;
    }

    // Only administrators can retrieve every order.
    @Transactional(readOnly = true)
    public List<PurchaseOrder> getAllOrders(String authenticatedEmail) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        return purchaseOrderRepository.findAll();
    }

    // Only administrators can filter all orders by status.
    @Transactional(readOnly = true)
    public List<PurchaseOrder> getOrdersByStatus(
            OrderStatus status,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        return purchaseOrderRepository.findByStatus(status);
    }

    // Only administrators can confirm orders.
    @Transactional
    public PurchaseOrder confirmOrder(
            Long id,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        PurchaseOrder order = findOrderById(id);

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

    // Only administrators can mark orders as shipped.
    @Transactional
    public PurchaseOrder shipOrder(
            Long id,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        PurchaseOrder order = findOrderById(id);

        try {
            order.ship();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        return purchaseOrderRepository.save(order);
    }

    // Only administrators can mark orders as delivered.
    @Transactional
    public PurchaseOrder deliverOrder(
            Long id,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        PurchaseOrder order = findOrderById(id);

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

    // Only administrators can cancel orders.
    @Transactional
    public PurchaseOrder cancelOrder(
            Long id,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        PurchaseOrder order = findOrderById(id);

        try {
            order.cancel();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        return purchaseOrderRepository.save(order);
    }

    private PurchaseOrder findOrderById(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
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

    private void verifyBuyerOwnsQuotation(
            User user,
            Quotation quotation
    ) {
        Organization organization = requireOrganization(user);

        Long buyerOrganizationId = quotation
                .getProcurementRequest()
                .getBuyerOrganization()
                .getId();

        if (!organization.getId().equals(buyerOrganizationId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot create an order from another buyer's quotation"
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
                "You do not have permission to access this purchase order"
        );
    }
}