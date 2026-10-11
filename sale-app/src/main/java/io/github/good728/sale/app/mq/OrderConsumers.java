package io.github.good728.sale.app.mq;

import io.github.good728.sale.app.mq.OrderMessages.CloseOrder;
import io.github.good728.sale.app.mq.OrderMessages.CreateOrder;
import io.github.good728.sale.app.service.OrderService;
import io.github.good728.sale.app.service.SeatInventoryService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

public final class OrderConsumers {
    private OrderConsumers() {}

    @Component
    @RocketMQMessageListener(topic = "sale-create-order", consumerGroup = "sale-create-order-group")
    public static class CreateConsumer implements RocketMQListener<CreateOrder> {
        private final OrderService orders;
        private final SeatInventoryService inventory;

        public CreateConsumer(OrderService orders, SeatInventoryService inventory) {
            this.orders = orders;
            this.inventory = inventory;
        }

        @Override
        public void onMessage(CreateOrder command) {
            try {
                if (orders.createIdempotently(command)) {
                    orders.sendTimeout(command);
                }
            } catch (RuntimeException e) {
                orders.closeIfUnpaid(new CloseOrder(
                        command.orderNo(), command.programId(), command.seatIds()));
                inventory.release(command.programId(), command.seatIds(), command.orderNo());
                // 已执行补偿，消息作为最终失败结束，避免重试在释放库存后创建悬空订单。
            }
        }
    }

    @Component
    @RocketMQMessageListener(topic = "sale-close-order", consumerGroup = "sale-close-order-group")
    public static class CloseConsumer implements RocketMQListener<CloseOrder> {
        private final OrderService orders;
        private final SeatInventoryService inventory;

        public CloseConsumer(OrderService orders, SeatInventoryService inventory) {
            this.orders = orders;
            this.inventory = inventory;
        }

        @Override
        public void onMessage(CloseOrder command) {
            if (orders.closeIfUnpaid(command)) {
                inventory.release(command.programId(), command.seatIds(), command.orderNo());
            }
        }
    }
}
