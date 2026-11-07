package com.tradeflow.api.inventory;

import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.product.InventorySummaryProjection;
import com.tradeflow.api.product.InventoryValuationProjection;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.product.ProductRepository;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "createdAt",
            "quantity",
            "transactionType",
            "previousQuantity",
            "newQuantity"
    );

    private static final Set<String> ALLOWED_LOW_STOCK_SORT_FIELDS = Set.of(
            "id",
            "name",
            "sku",
            "availableQuantity",
            "lowStockThreshold",
            "createdAt"
    );

    public InventoryService(
            ProductRepository productRepository,
            InventoryTransactionRepository transactionRepository,
            UserRepository userRepository
    ) {
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // Retrieves paginated inventory transaction history for administrators.
    @Transactional(readOnly = true)
    public Page<InventoryTransactionResponse> getTransactionHistory(
            int page,
            int size,
            String sortBy,
            String direction,
            Long productId,
            InventoryTransactionType transactionType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String authenticatedEmail
    ) {
        // Verify administrator access.
        getAuthenticatedAdmin(authenticatedEmail);

        // Validate pagination.
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page index cannot be negative"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE
            );
        }

        // Validate sorting field.
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid sort field. Allowed fields: " + ALLOWED_SORT_FIELDS
            );
        }

        // Validate sorting direction.
        Sort.Direction sortDirection;

        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sort direction must be 'asc' or 'desc'"
            );
        }

        // Validate product ID.
        if (productId != null && productId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product ID must be greater than zero"
            );
        }

        // Validate date range.
        if (startDate != null && endDate != null
                && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date cannot be after end date"
            );
        }

        // Build pagination and sorting.
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(sortDirection, sortBy)
        );

        // Combine optional filters.
        Specification<InventoryTransaction> specification =
                Specification.<InventoryTransaction>unrestricted()
                        .and(InventoryTransactionSpecification.hasProductId(productId))
                        .and(InventoryTransactionSpecification.hasTransactionType(transactionType))
                        .and(InventoryTransactionSpecification.hasStartDate(startDate))
                        .and(InventoryTransactionSpecification.hasEndDate(endDate));

        // Retrieve transactions and map them to response DTOs.
        return transactionRepository.findAll(specification, pageable)
                .map(InventoryTransactionResponse::from);
    }

    // Retrieves paginated low-stock and out-of-stock products for administrators.
    @Transactional(readOnly = true)
    public Page<LowStockProductResponse> getLowStockProducts(
            int page,
            int size,
            String sortBy,
            String direction,
            String status,
            String authenticatedEmail
    ) {
        // Verify administrator access.
        getAuthenticatedAdmin(authenticatedEmail);

        // Validate pagination.
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page index cannot be negative"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE
            );
        }

        // Validate sorting field.
        if (sortBy == null || !ALLOWED_LOW_STOCK_SORT_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid sort field. Allowed fields: "
                            + ALLOWED_LOW_STOCK_SORT_FIELDS
            );
        }

        // Validate sorting direction.
        Sort.Direction sortDirection;

        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sort direction must be 'asc' or 'desc'"
            );
        }

        // Validate optional stock status.
        String normalizedStatus = status == null || status.isBlank()
                ? null
                : status.trim().toUpperCase(Locale.ROOT);

        if (normalizedStatus != null
                && !Set.of("LOW_STOCK", "OUT_OF_STOCK")
                .contains(normalizedStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status must be 'LOW_STOCK' or 'OUT_OF_STOCK'"
            );
        }

        // Build pagination and sorting.
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(sortDirection, sortBy)
        );

        // Retrieve products whose available quantity is at or below threshold.
        Specification<Product> specification =
                Specification.<Product>unrestricted()
                        .and(com.tradeflow.api.product.ProductSpecification.isLowStock())
                        .and(com.tradeflow.api.product.ProductSpecification
                                .hasStockStatus(normalizedStatus));

        return productRepository.findAll(specification, pageable)
                .map(LowStockProductResponse::from);
    }

    // Retrieves aggregated inventory dashboard metrics for administrators.
    @Transactional(readOnly = true)
    public InventorySummaryResponse getInventorySummary(
            String authenticatedEmail
    ) {
        // Verify administrator access.
        getAuthenticatedAdmin(authenticatedEmail);

        // Retrieve aggregated metrics directly from the database.
        InventorySummaryProjection summary =
                productRepository.getInventorySummary();

        return new InventorySummaryResponse(
                summary.getTotalProducts(),
                summary.getTotalInventoryUnits(),
                summary.getLowStockProducts(),
                summary.getOutOfStockProducts(),
                summary.getHealthyStockProducts()
        );
    }

    // Retrieves estimated inventory valuation at selling price for administrators.
    @Transactional(readOnly = true)
    public InventoryValuationResponse getInventoryValuation(
            String authenticatedEmail
    ) {
        // Verify administrator access.
        getAuthenticatedAdmin(authenticatedEmail);

        // Retrieve aggregated valuation directly from the database.
        InventoryValuationProjection valuation =
                productRepository.getInventoryValuation();

        Long totalInventoryUnits = valuation.getTotalInventoryUnits();
        BigDecimal totalInventoryValue = valuation.getTotalInventoryValue();

        // Calculate quantity-weighted average unit value.
        BigDecimal averageUnitValue = totalInventoryUnits == 0L
                ? BigDecimal.ZERO.setScale(2)
                : totalInventoryValue.divide(
                BigDecimal.valueOf(totalInventoryUnits),
                2,
                RoundingMode.HALF_UP
        );

        return new InventoryValuationResponse(
                valuation.getTotalProducts(),
                totalInventoryUnits,
                totalInventoryValue,
                averageUnitValue
        );
    }

    // Retrieves inventory movement analytics for administrators.
    @Transactional(readOnly = true)
    public InventoryMovementAnalyticsResponse getInventoryMovementAnalytics(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String authenticatedEmail
    ) {
        // Verify administrator access.
        getAuthenticatedAdmin(authenticatedEmail);

        // Validate date range.
        if (startDate != null && endDate != null
                && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date cannot be after end date"
            );
        }

        // Retrieve aggregated movement metrics directly from the database.
        InventoryMovementAnalyticsProjection analytics =
                transactionRepository.getInventoryMovementAnalytics(
                        startDate,
                        endDate
                );

        Long totalStockIn = analytics.getTotalStockIn();
        Long totalStockOut = analytics.getTotalStockOut();
        Long adjustmentIncrease = analytics.getAdjustmentIncrease();
        Long adjustmentDecrease = analytics.getAdjustmentDecrease();

        // Calculate net physical inventory movement.
        Long netInventoryMovement =
                totalStockIn
                        - totalStockOut
                        + adjustmentIncrease
                        - adjustmentDecrease;

        return new InventoryMovementAnalyticsResponse(
                totalStockIn,
                totalStockOut,
                adjustmentIncrease,
                adjustmentDecrease,
                netInventoryMovement,
                analytics.getTotalTransactions()
        );
    }
    // Admin manually adds stock to a product.
    @Transactional
    public Product stockIn(
            StockInRequest request,
            String authenticatedEmail
    ) {
        User performedBy = getAuthenticatedAdmin(authenticatedEmail);

        validateQuantity(request.quantity());

        Product product = getProductForUpdate(request.productId());

        int previousQuantity = product.getAvailableQuantity();

        if (previousQuantity > Integer.MAX_VALUE - request.quantity()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inventory quantity exceeds the supported limit"
            );
        }

        int newQuantity = previousQuantity + request.quantity();

        product.setAvailableQuantity(newQuantity);
        productRepository.save(product);

        recordTransaction(
                product,
                null,
                performedBy,
                InventoryTransactionType.STOCK_IN,
                request.quantity(),
                previousQuantity,
                newQuantity,
                request.reason().trim()
        );

        return product;
    }

    // Admin manually removes stock from available inventory.
    @Transactional
    public Product stockOut(
            StockOutRequest request,
            String authenticatedEmail
    ) {
        User performedBy = getAuthenticatedAdmin(authenticatedEmail);

        validateQuantity(request.quantity());

        Product product = getProductForUpdate(request.productId());

        int previousQuantity = product.getAvailableQuantity();

        if (previousQuantity < request.quantity()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient inventory for stock-out"
            );
        }

        int newQuantity = previousQuantity - request.quantity();

        product.setAvailableQuantity(newQuantity);
        productRepository.save(product);

        recordTransaction(
                product,
                null,
                performedBy,
                InventoryTransactionType.STOCK_OUT,
                request.quantity(),
                previousQuantity,
                newQuantity,
                request.reason().trim()
        );

        return product;
    }

    // Admin manually adjusts stock to correct physical inventory differences.
    @Transactional
    public Product adjustStock(
            StockAdjustmentRequest request,
            String authenticatedEmail
    ) {
        User performedBy = getAuthenticatedAdmin(authenticatedEmail);

        int adjustmentQuantity = request.adjustmentQuantity();

        if (adjustmentQuantity == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Adjustment quantity cannot be zero"
            );
        }

        Product product = getProductForUpdate(request.productId());

        int previousQuantity = product.getAvailableQuantity();

        long calculatedQuantity =
                (long) previousQuantity + adjustmentQuantity;

        if (calculatedQuantity < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stock adjustment cannot reduce inventory below zero"
            );
        }

        if (calculatedQuantity > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inventory quantity exceeds the supported limit"
            );
        }

        int newQuantity = (int) calculatedQuantity;
        int absoluteQuantity = (int) Math.abs((long) adjustmentQuantity);

        product.setAvailableQuantity(newQuantity);
        productRepository.save(product);

        recordTransaction(
                product,
                null,
                performedBy,
                InventoryTransactionType.ADJUSTMENT,
                absoluteQuantity,
                previousQuantity,
                newQuantity,
                request.reason().trim()
        );

        return product;
    }

    @Transactional
    public void reserveForOrder(
            Long productId,
            int quantity,
            User performedBy,
            PurchaseOrder purchaseOrder
    ) {
        validateQuantity(quantity);

        Product product = getProductForUpdate(productId);

        int previousQuantity = product.getAvailableQuantity();

        if (previousQuantity < quantity) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient inventory for this order"
            );
        }

        int newQuantity = previousQuantity - quantity;
        product.setAvailableQuantity(newQuantity);

        productRepository.save(product);

        recordTransaction(
                product,
                purchaseOrder,
                performedBy,
                InventoryTransactionType.RESERVED,
                quantity,
                previousQuantity,
                newQuantity,
                "Inventory reserved for purchase order"
        );
    }

    @Transactional
    public void releaseForOrder(
            Long productId,
            int quantity,
            User performedBy,
            PurchaseOrder purchaseOrder
    ) {
        validateQuantity(quantity);

        Product product = getProductForUpdate(productId);

        int previousQuantity = product.getAvailableQuantity();

        if (previousQuantity > Integer.MAX_VALUE - quantity) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inventory quantity exceeds the supported limit"
            );
        }

        int newQuantity = previousQuantity + quantity;
        product.setAvailableQuantity(newQuantity);

        productRepository.save(product);

        recordTransaction(
                product,
                purchaseOrder,
                performedBy,
                InventoryTransactionType.RELEASED,
                quantity,
                previousQuantity,
                newQuantity,
                "Inventory released after purchase order cancellation"
        );
    }

    // Records physical dispatch without deducting reserved inventory again.
    @Transactional
    public void recordShipmentForOrder(
            Long productId,
            int quantity,
            User performedBy,
            PurchaseOrder purchaseOrder
    ) {
        validateQuantity(quantity);

        Product product = getProductForUpdate(productId);

        // availableQuantity was already reduced when the order was confirmed.
        // Keep the balance unchanged and record the dispatch event only.
        int availableQuantity = product.getAvailableQuantity();

        recordTransaction(
                product,
                purchaseOrder,
                performedBy,
                InventoryTransactionType.SHIPPED,
                quantity,
                availableQuantity,
                availableQuantity,
                "Inventory dispatched for purchase order"
        );
    }

    private User getAuthenticatedAdmin(String email) {
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

        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this action"
            );
        }

        return user;
    }

    private Product getProductForUpdate(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inventory quantity must be greater than zero"
            );
        }
    }

    private void recordTransaction(
            Product product,
            PurchaseOrder purchaseOrder,
            User performedBy,
            InventoryTransactionType transactionType,
            int quantity,
            int previousQuantity,
            int newQuantity,
            String reason
    ) {
        InventoryTransaction transaction = new InventoryTransaction(
                product,
                purchaseOrder,
                performedBy,
                transactionType,
                quantity,
                previousQuantity,
                newQuantity,
                reason
        );

        transactionRepository.save(transaction);
    }
}
