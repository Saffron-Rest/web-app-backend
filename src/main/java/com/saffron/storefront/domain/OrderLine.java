package com.saffron.storefront.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;

@Entity
@Table(name = "order_lines", indexes = {
        @Index(name = "ix_order_lines_order", columnList = "order_id")
})
public class OrderLine {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Snapshot of the product name at checkout — keeps order history stable across edits. */
    @Column(name = "name_snapshot", nullable = false, length = 200)
    private String nameSnapshot;

    @Column(nullable = false)
    private int quantity = 1;

    /** Unit price at checkout time. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    public String getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public void setOrder(CustomerOrder order) { this.order = order; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getNameSnapshot() { return nameSnapshot; }
    public void setNameSnapshot(String s) { this.nameSnapshot = s; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int q) { this.quantity = q; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal p) { this.unitPrice = p; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal t) { this.lineTotal = t; }
}
