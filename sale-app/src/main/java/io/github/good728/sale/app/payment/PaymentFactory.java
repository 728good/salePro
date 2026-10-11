package io.github.good728.sale.app.payment;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentFactory {
    private final Map<String, PaymentStrategy> strategies;

    public PaymentFactory(List<PaymentStrategy> strategies) {
        this.strategies = strategies.stream().collect(Collectors.toUnmodifiableMap(
                strategy -> strategy.type().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public PaymentStrategy get(String type) {
        if (type == null) throw new IllegalArgumentException("支付类型不能为空");
        PaymentStrategy strategy = strategies.get(type.toUpperCase(Locale.ROOT));
        if (strategy == null) throw new IllegalArgumentException("不支持的支付类型: " + type);
        return strategy;
    }
}
