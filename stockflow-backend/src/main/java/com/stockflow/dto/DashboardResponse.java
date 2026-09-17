package com.stockflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardResponse {
    private DashboardResponse() {}
    public record Metrics(long totalProducts, long totalCustomers, long totalSuppliers,
                          long lowStockCount, BigDecimal totalInventoryValue) {}
    public record OrderSummary(Long id, String orderNumber, String partyName, LocalDate orderDate,
                               String status, BigDecimal totalAmount) {}
    public record DailyValue(LocalDate date, BigDecimal value) {}
    public record MovementValue(String type, long quantity) {}
    public record TopProduct(Long productId, String sku, String name, long quantity, BigDecimal salesValue) {}
    public record Report(LocalDate dateFrom, LocalDate dateTo, BigDecimal totalPurchaseValue,
                         BigDecimal totalSalesValue, List<OrderSummary> recentPurchaseOrders,
                         List<OrderSummary> recentSalesOrders, List<TransactionResponse> recentStockMovements,
                         List<TopProduct> topSellingProducts, List<DailyValue> purchases,
                         List<DailyValue> sales, List<MovementValue> stockMovements) {}
}
