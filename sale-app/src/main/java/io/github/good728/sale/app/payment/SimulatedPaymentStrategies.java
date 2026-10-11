package io.github.good728.sale.app.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

final class SimulatedPaymentStrategies {
    private SimulatedPaymentStrategies() {}

    private static PaymentStrategy.PaymentResult approved(String prefix) {
        return new PaymentStrategy.PaymentResult(
                true, prefix + "-" + UUID.randomUUID().toString().replace("-", ""), "模拟支付成功");
    }

    @Component
    static class Mock implements PaymentStrategy {
        public String type() { return "MOCK"; }
        public PaymentResult pay(String orderNo, BigDecimal amount) { return approved("mock"); }
    }

    /** 仅为可替换适配器，不连接支付宝或使用真实密钥。 */
    @Component
    static class AlipayAdapter implements PaymentStrategy {
        public String type() { return "ALIPAY"; }
        public PaymentResult pay(String orderNo, BigDecimal amount) { return approved("alipay-sandbox"); }
    }

    /** 仅为可替换适配器，不连接微信支付或使用真实密钥。 */
    @Component
    static class WechatAdapter implements PaymentStrategy {
        public String type() { return "WECHAT"; }
        public PaymentResult pay(String orderNo, BigDecimal amount) { return approved("wechat-sandbox"); }
    }
}
