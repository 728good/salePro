package io.github.good728.sale.app.payment;

import java.math.BigDecimal;

public interface PaymentStrategy {
    String type();
    PaymentResult pay(String orderNo, BigDecimal amount);
    record PaymentResult(boolean success, String transactionId, String message) {}
}
