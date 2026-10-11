package io.github.good728.sale.app.mq;

import java.util.List;

public final class OrderMessages {
    private OrderMessages() {}

    public record CreateOrder(String orderNo, Long userId, Long programId,
                              List<Long> seatIds, String paymentType) {}
    public record CloseOrder(String orderNo, Long programId, List<Long> seatIds) {}
}
