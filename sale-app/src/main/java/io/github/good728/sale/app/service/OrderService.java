package io.github.good728.sale.app.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.good728.sale.app.domain.Entities.OrderItem;
import io.github.good728.sale.app.domain.Entities.SaleOrder;
import io.github.good728.sale.app.domain.Entities.Seat;
import io.github.good728.sale.app.mapper.OrderItemMapper;
import io.github.good728.sale.app.mapper.OrderMapper;
import io.github.good728.sale.app.mapper.SeatMapper;
import io.github.good728.sale.app.mq.OrderMessages.CloseOrder;
import io.github.good728.sale.app.mq.OrderMessages.CreateOrder;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private final SeatInventoryService inventory;
    private final RocketMQTemplate rocketMQ;
    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final SeatMapper seatMapper;

    public OrderService(SeatInventoryService inventory, RocketMQTemplate rocketMQ,
                        OrderMapper orderMapper, OrderItemMapper itemMapper, SeatMapper seatMapper) {
        this.inventory = inventory;
        this.rocketMQ = rocketMQ;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.seatMapper = seatMapper;
    }

    public String reserve(Long userId, Long programId, List<Long> seatIds, String paymentType) {
        if (userId == null || programId == null || seatIds == null || seatIds.isEmpty()
                || seatIds.size() != new HashSet<>(seatIds).size()) {
            throw new IllegalArgumentException("下单参数不合法");
        }
        String orderNo = UUID.randomUUID().toString().replace("-", "");
        if (!inventory.reserve(programId, seatIds, orderNo)) {
            throw new IllegalStateException("座位不存在或已被锁定");
        }
        CreateOrder command = new CreateOrder(orderNo, userId, programId, List.copyOf(seatIds), paymentType);
        try {
            var result = rocketMQ.syncSend("sale-create-order", MessageBuilder.withPayload(command).build(), 3_000);
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("订单消息发送失败");
            }
            return orderNo;
        } catch (RuntimeException e) {
            inventory.release(programId, seatIds, orderNo);
            throw e;
        }
    }

    @Transactional
    public boolean createIdempotently(CreateOrder command) {
        if (orderMapper.selectCount(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getOrderNo, command.orderNo())) > 0) return false;
        List<Seat> seats = seatMapper.selectList(Wrappers.<Seat>lambdaQuery()
                .eq(Seat::getProgramId, command.programId()).in(Seat::getId, command.seatIds()));
        if (seats.size() != command.seatIds().size()) throw new IllegalStateException("座位数据不完整");
        BigDecimal amount = seats.stream().map(Seat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        SaleOrder order = new SaleOrder();
        order.setOrderNo(command.orderNo());
        order.setUserId(command.userId());
        order.setProgramId(command.programId());
        order.setAmount(amount);
        order.setStatus("PENDING_PAYMENT");
        order.setPaymentType(command.paymentType());
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        for (Seat seat : seats) {
            int changed = seatMapper.update(null, Wrappers.<Seat>lambdaUpdate()
                    .eq(Seat::getId, seat.getId()).eq(Seat::getStatus, "AVAILABLE")
                    .set(Seat::getStatus, "LOCKED"));
            if (changed != 1) throw new IllegalStateException("座位已售出");
            OrderItem item = new OrderItem();
            item.setOrderNo(command.orderNo());
            item.setSeatId(seat.getId());
            item.setSeatNo(seat.getSeatNo());
            item.setPrice(seat.getPrice());
            itemMapper.insert(item);
        }
        return true;
    }

    public void sendTimeout(CreateOrder command) {
        CloseOrder close = new CloseOrder(command.orderNo(), command.programId(), command.seatIds());
        rocketMQ.syncSend("sale-close-order", MessageBuilder.withPayload(close).build(), 3_000, 17);
    }

    @Transactional
    public boolean closeIfUnpaid(CloseOrder command) {
        int changed = orderMapper.update(null, Wrappers.<SaleOrder>lambdaUpdate()
                .eq(SaleOrder::getOrderNo, command.orderNo())
                .eq(SaleOrder::getStatus, "PENDING_PAYMENT")
                .set(SaleOrder::getStatus, "CLOSED"));
        if (changed == 0) return false;
        seatMapper.update(null, Wrappers.<Seat>lambdaUpdate()
                .in(Seat::getId, command.seatIds()).eq(Seat::getStatus, "LOCKED")
                .set(Seat::getStatus, "AVAILABLE"));
        return true;
    }

    public SaleOrder find(String orderNo) {
        return orderMapper.selectOne(Wrappers.<SaleOrder>lambdaQuery().eq(SaleOrder::getOrderNo, orderNo));
    }
}
