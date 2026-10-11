package io.github.good728.sale.app.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class Entities {
    private Entities() {}

    @TableName("program")
    public static class Program {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String name;
        private String venue;
        private LocalDateTime showTime;
        private String status;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVenue() { return venue; }
        public void setVenue(String venue) { this.venue = venue; }
        public LocalDateTime getShowTime() { return showTime; }
        public void setShowTime(LocalDateTime showTime) { this.showTime = showTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    @TableName("seat")
    public static class Seat {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long programId;
        private String seatNo;
        private BigDecimal price;
        private String status;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getProgramId() { return programId; }
        public void setProgramId(Long programId) { this.programId = programId; }
        public String getSeatNo() { return seatNo; }
        public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    @TableName("sale_order")
    public static class SaleOrder {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String orderNo;
        private Long userId;
        private Long programId;
        private BigDecimal amount;
        private String status;
        private String paymentType;
        private LocalDateTime createdAt;
        private LocalDateTime paidAt;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getProgramId() { return programId; }
        public void setProgramId(Long programId) { this.programId = programId; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPaymentType() { return paymentType; }
        public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    }

    @TableName("order_item")
    public static class OrderItem {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String orderNo;
        private Long seatId;
        private String seatNo;
        private BigDecimal price;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Long getSeatId() { return seatId; }
        public void setSeatId(Long seatId) { this.seatId = seatId; }
        public String getSeatNo() { return seatNo; }
        public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }
}
