package io.github.good728.sale.app.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.good728.sale.app.domain.Entities.OrderItem;
import io.github.good728.sale.app.domain.Entities.SaleOrder;
import io.github.good728.sale.app.domain.Entities.Seat;
import io.github.good728.sale.app.mapper.OrderItemMapper;
import io.github.good728.sale.app.mapper.OrderMapper;
import io.github.good728.sale.app.mapper.SeatMapper;
import io.github.good728.sale.app.payment.PaymentFactory;
import io.github.good728.sale.app.payment.PaymentStrategy.PaymentResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {
    private final PaymentFactory factory;
    private final OrderMapper orders;
    private final OrderItemMapper items;
    private final SeatMapper seats;
    private final SeatInventoryService inventory;

    public PaymentService(PaymentFactory factory, OrderMapper orders, OrderItemMapper items,
                          SeatMapper seats, SeatInventoryService inventory) {
        this.factory = factory;
        this.orders = orders;
        this.items = items;
        this.seats = seats;
        this.inventory = inventory;
    }

    @Transactional
    public PaymentResult pay(String orderNo) {
        SaleOrder order = orders.selectOne(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getOrderNo, orderNo));
        if (order == null || !"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new IllegalStateException("订单不存在或不可支付");
        }
        PaymentResult result = factory.get(order.getPaymentType()).pay(orderNo, order.getAmount());
        if (result.success()) confirm(order);
        return result;
    }

    private void confirm(SaleOrder order) {
        int changed = orders.update(null, Wrappers.<SaleOrder>lambdaUpdate()
                .eq(SaleOrder::getOrderNo, order.getOrderNo())
                .eq(SaleOrder::getStatus, "PENDING_PAYMENT")
                .set(SaleOrder::getStatus, "PAID").set(SaleOrder::getPaidAt, LocalDateTime.now()));
        if (changed != 1) throw new IllegalStateException("订单状态已变化");
        List<Long> seatIds = items.selectList(Wrappers.<OrderItem>lambdaQuery()
                        .eq(OrderItem::getOrderNo, order.getOrderNo()))
                .stream().map(OrderItem::getSeatId).toList();
        seats.update(null, Wrappers.<Seat>lambdaUpdate().in(Seat::getId, seatIds)
                .eq(Seat::getStatus, "LOCKED").set(Seat::getStatus, "SOLD"));
        if (!inventory.markSold(order.getProgramId(), seatIds, order.getOrderNo())) {
            throw new IllegalStateException("库存确认失败");
        }
    }
}
