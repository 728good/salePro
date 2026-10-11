package io.github.good728.sale.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
public class TicketGuardFilter implements GlobalFilter, Ordered {
    private static final DefaultRedisScript<Long> GUARD = new DefaultRedisScript<>("""
            local count=redis.call('INCR',KEYS[1])
            if count==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end
            if count>tonumber(ARGV[2]) then return -1 end
            local expected=redis.call('GET',KEYS[2])
            if (not expected) or expected~=ARGV[3] then return 0 end
            redis.call('DEL',KEYS[2])
            redis.call('SET',KEYS[3],'1','EX',ARGV[4])
            return 1
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final int windowSeconds;
    private final int maxRequests;

    public TicketGuardFilter(ReactiveStringRedisTemplate redis,
                             @Value("${sale.rate-limit.window-seconds:1}") int windowSeconds,
                             @Value("${sale.rate-limit.max-requests:10}") int maxRequests) {
        this.redis = redis;
        this.windowSeconds = windowSeconds;
        this.maxRequests = maxRequests;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!"/api/orders/reserve".equals(exchange.getRequest().getPath().value())) {
            return chain.filter(removeForgedTicket(exchange));
        }
        String token = exchange.getRequest().getHeaders().getFirst("X-Captcha-Token");
        String code = exchange.getRequest().getHeaders().getFirst("X-Captcha-Code");
        if (token == null || code == null) return reject(exchange, HttpStatus.FORBIDDEN);
        String ip = exchange.getRequest().getRemoteAddress() == null ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        long window = System.currentTimeMillis() / (windowSeconds * 1000L);
        String ticket = UUID.randomUUID().toString();
        List<String> keys = List.of("sale:rate:" + ip + ":" + window,
                "sale:captcha:" + token, "sale:gateway:ticket:" + ticket);
        return redis.execute(GUARD, keys,
                        List.of(String.valueOf(windowSeconds), String.valueOf(maxRequests), code, "10"))
                .next().flatMap(result -> {
                    if (result == null || result == 0) return reject(exchange, HttpStatus.FORBIDDEN);
                    if (result == -1) return reject(exchange, HttpStatus.TOO_MANY_REQUESTS);
                    ServerWebExchange guarded = exchange.mutate().request(request -> request.headers(headers -> {
                        headers.remove("X-Captcha-Token");
                        headers.remove("X-Captcha-Code");
                        headers.remove("X-Sale-Gateway-Ticket");
                        headers.add("X-Sale-Gateway-Ticket", ticket);
                    })).build();
                    return chain.filter(guarded);
                });
    }

    private ServerWebExchange removeForgedTicket(ServerWebExchange exchange) {
        return exchange.mutate().request(request ->
                request.headers(headers -> headers.remove("X-Sale-Gateway-Ticket"))).build();
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
