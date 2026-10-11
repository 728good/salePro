package io.github.good728.sale.app.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class CaptchaService {
    private static final DefaultRedisScript<Long> VERIFY = new DefaultRedisScript<>("""
            local v=redis.call('GET',KEYS[1])
            if v and v==ARGV[1] then redis.call('DEL',KEYS[1]); return 1 end
            return 0
            """, Long.class);
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;

    public CaptchaService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Captcha create() {
        String token = UUID.randomUUID().toString();
        String code = String.format("%04d", random.nextInt(10_000));
        redis.opsForValue().set("sale:captcha:" + token, code, Duration.ofMinutes(2));
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='140' height='48'>"
                + "<rect width='100%' height='100%' fill='#f4f4f4'/>"
                + "<text x='22' y='33' font-size='28' letter-spacing='8' fill='#222'>" + code + "</text></svg>";
        return new Captcha(token, "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8)), 120);
    }

    public boolean verify(String token, String code) {
        if (token == null || code == null) return false;
        return Long.valueOf(1).equals(redis.execute(
                VERIFY, List.of("sale:captcha:" + token), code));
    }

    public boolean consumeGatewayTicket(String ticket) {
        return ticket != null && Boolean.TRUE.equals(redis.delete("sale:gateway:ticket:" + ticket));
    }

    public record Captcha(String token, String image, int expiresInSeconds) {}
}
