package com.stockflow.service;

import com.stockflow.dto.DashboardResponse.*;
import com.stockflow.dto.TransactionResponse;
import com.stockflow.entity.InventoryTransaction;
import com.stockflow.exception.DomainException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final EntityManager em;
    public DashboardService(EntityManager em) { this.em = em; }

    public Metrics metrics() {
        return new Metrics(count("Product"), count("Customer"), count("Supplier"),
            em.createQuery("select count(p) from Product p where p.quantityInStock <= p.reorderLevel", Long.class).getSingleResult(),
            money(em.createQuery("select sum(p.costPrice * p.quantityInStock) from Product p", BigDecimal.class).getSingleResult()));
    }
    private long count(String entity) { return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult(); }
    private BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value; }

    public Report report(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to))
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "From date must be on or before to date");
        if ((from != null && (from.getYear() < 1 || from.getYear() > 9999)) ||
            (to != null && (to.getYear() < 1 || to.getYear() > 9999)))
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Dates must be between years 0001 and 9999");
        String dates = (from == null ? "" : " and o.orderDate >= :from") + (to == null ? "" : " and o.orderDate <= :to");
        String movementDates = (from == null ? "" : " and o.createdAt >= :from") + (to == null ? "" : " and o.createdAt < :to");
        var recentMovements = range(em.createQuery("select o from InventoryTransaction o where 1=1" + movementDates + " order by o.createdAt desc, o.id desc", InventoryTransaction.class), from, to, true)
            .setMaxResults(8).getResultList().stream().map(TransactionResponse::from).toList();
        var movementTotals = range(em.createQuery("select o.transactionType, sum(o.quantity) from InventoryTransaction o where 1=1" + movementDates + " group by o.transactionType order by o.transactionType", Object[].class), from, to, true)
            .getResultList().stream().map(r -> new MovementValue(r[0].toString(), ((Number)r[1]).longValue())).toList();
        var top = range(em.createQuery("select i.product.id, i.product.sku, i.product.name, sum(i.quantity), sum(i.lineTotal) from SalesOrderItem i join i.order o where o.status = com.stockflow.entity.OrderStatus.FULFILLED" + dates + " group by i.product.id, i.product.sku, i.product.name order by sum(i.quantity) desc, i.product.id asc", Object[].class), from, to, false)
            .setMaxResults(5).getResultList().stream().map(r -> new TopProduct((Long)r[0], (String)r[1], (String)r[2], ((Number)r[3]).longValue(), (BigDecimal)r[4])).toList();
        return new Report(from, to, total(true, dates, from, to), total(false, dates, from, to),
            recent(true, dates, from, to), recent(false, dates, from, to), recentMovements, top,
            daily(true, dates, from, to), daily(false, dates, from, to), movementTotals);
    }
    private String entity(boolean purchase) { return purchase ? "PurchaseOrder" : "SalesOrder"; }
    private String completed(boolean purchase) { return purchase ? "com.stockflow.entity.PurchaseOrderStatus.RECEIVED" : "com.stockflow.entity.OrderStatus.FULFILLED"; }
    private BigDecimal total(boolean purchase, String dates, LocalDate from, LocalDate to) {
        return money(range(em.createQuery("select sum(o.totalAmount) from " + entity(purchase) + " o where o.status = " + completed(purchase) + dates, BigDecimal.class), from, to, false).getSingleResult());
    }
    private List<DailyValue> daily(boolean purchase, String dates, LocalDate from, LocalDate to) {
        return range(em.createQuery("select o.orderDate, sum(o.totalAmount) from " + entity(purchase) + " o where o.status = " + completed(purchase) + dates + " group by o.orderDate order by o.orderDate", Object[].class), from, to, false)
            .getResultList().stream().map(r -> new DailyValue((LocalDate)r[0], (BigDecimal)r[1])).toList();
    }
    private List<OrderSummary> recent(boolean purchase, String dates, LocalDate from, LocalDate to) {
        return range(em.createQuery("select o.id, o.orderNumber, o." + (purchase ? "supplier" : "customer") + ".name, o.orderDate, o.status, o.totalAmount from " + entity(purchase) + " o where 1=1" + dates + " order by o.orderDate desc, o.id desc", Object[].class), from, to, false)
            .setMaxResults(8).getResultList().stream().map(r -> new OrderSummary((Long)r[0], (String)r[1], (String)r[2], (LocalDate)r[3], r[4].toString(), (BigDecimal)r[5])).toList();
    }
    private <T> TypedQuery<T> range(TypedQuery<T> query, LocalDate from, LocalDate to, boolean instant) {
        if (from != null) query.setParameter("from", instant ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : from);
        if (to != null) query.setParameter("to", instant ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : to);
        return query;
    }
}
