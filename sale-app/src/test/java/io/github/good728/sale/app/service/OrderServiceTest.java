package io.github.good728.sale.app.service;

import io.github.good728.sale.app.mapper.OrderItemMapper;
import io.github.good728.sale.app.mapper.OrderMapper;
import io.github.good728.sale.app.mapper.SeatMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.messaging.Message;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    @Test
    void releasesExactlyOwnedSeatsWhenMessageSendFails() {
        SeatInventoryService inventory = mock(SeatInventoryService.class);
        RocketMQTemplate rocket = mock(RocketMQTemplate.class);
        when(inventory.reserve(eq(1L), eq(List.of(10L, 11L)), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("broker down")).when(rocket)
                .syncSend(eq("sale-create-order"), ArgumentMatchers.<Message<?>>any(), eq(3_000L));
        OrderService service = new OrderService(inventory, rocket, mock(OrderMapper.class),
                mock(OrderItemMapper.class), mock(SeatMapper.class));

        assertThrows(IllegalStateException.class,
                () -> service.reserve(9L, 1L, List.of(10L, 11L), "MOCK"));
        verify(inventory).release(eq(1L), eq(List.of(10L, 11L)), anyString());
    }

    @Test
    void rejectsDuplicateSeatIdsBeforeRedisMutation() {
        SeatInventoryService inventory = mock(SeatInventoryService.class);
        OrderService service = new OrderService(inventory, mock(RocketMQTemplate.class),
                mock(OrderMapper.class), mock(OrderItemMapper.class), mock(SeatMapper.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.reserve(9L, 1L, List.of(10L, 10L), "MOCK"));
        verifyNoInteractions(inventory);
    }
}
