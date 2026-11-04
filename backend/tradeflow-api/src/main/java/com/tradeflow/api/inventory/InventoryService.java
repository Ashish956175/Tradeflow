package com.tradeflow.api.inventory;

import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.product.ProductRepository;
import com.tradeflow.api.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;

    public InventoryService(
            ProductRepository productRepository,
            InventoryTransactionRepository transactionRepository
    ) {
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
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
