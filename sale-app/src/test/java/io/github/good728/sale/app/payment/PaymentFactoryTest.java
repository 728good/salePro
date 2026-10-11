package io.github.good728.sale.app.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentFactoryTest {
    @Test
    void selectsStrategiesCaseInsensitively() {
        PaymentStrategy mock = strategy("MOCK");
        PaymentStrategy alipay = strategy("ALIPAY");
        PaymentStrategy wechat = strategy("WECHAT");
        PaymentFactory factory = new PaymentFactory(List.of(mock, alipay, wechat));

        assertThat(factory.get("mock")).isSameAs(mock);
        assertThat(factory.get("ALIPAY")).isSameAs(alipay);
        assertThat(factory.get("wechat")).isSameAs(wechat);
    }

    @Test
    void rejectsUnknownChannel() {
        PaymentFactory factory = new PaymentFactory(List.of(strategy("MOCK")));
        assertThatThrownBy(() -> factory.get("CARD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PaymentStrategy strategy(String type) {
        return new PaymentStrategy() {
            public String type() {
                return type;
            }

            public PaymentResult pay(String orderNo, BigDecimal amount) {
                return new PaymentResult(true, "test", "ok");
            }
        };
    }
}
