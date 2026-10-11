package io.github.good728.sale.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.good728.sale.app.domain.Entities.Program;
import io.github.good728.sale.app.mapper.ProgramMapper;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ProgramService {
    private final ProgramMapper mapper;
    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;
    private final Cache<Long, Program> local = Caffeine.newBuilder()
            .maximumSize(2_000).expireAfterWrite(Duration.ofMinutes(2)).build();
    private RBloomFilter<Long> bloom;

    public ProgramService(ProgramMapper mapper, StringRedisTemplate redis,
                          RedissonClient redisson, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.redis = redis;
        this.redisson = redisson;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initializeBloom() {
        bloom = redisson.getBloomFilter("sale:program:bloom");
        bloom.tryInit(100_000, 0.001);
        mapper.selectObjs(Wrappers.<Program>query().select("id"))
                .forEach(id -> bloom.add(((Number) id).longValue()));
    }

    public IPage<Program> search(String keyword, long page, long size) {
        return mapper.selectPage(Page.of(page, Math.min(size, 100)),
                Wrappers.<Program>lambdaQuery()
                        .like(keyword != null && !keyword.isBlank(), Program::getName, keyword)
                        .eq(Program::getStatus, "ON_SALE")
                        .orderByAsc(Program::getShowTime));
    }

    public Program detail(Long id) {
        if (!bloom.contains(id)) return null;
        Program cached = local.getIfPresent(id);
        if (cached != null) return cached;
        String cacheKey = "sale:program:" + id;
        Program fromRedis = read(redis.opsForValue().get(cacheKey));
        if (fromRedis != null) {
            local.put(id, fromRedis);
            return fromRedis;
        }
        RLock lock = redisson.getLock("sale:program:lock:" + id);
        try {
            if (!lock.tryLock(2, 5, TimeUnit.SECONDS)) return null;
            fromRedis = read(redis.opsForValue().get(cacheKey));
            if (fromRedis == null) {
                fromRedis = mapper.selectById(id);
                if (fromRedis != null) redis.opsForValue().set(cacheKey, write(fromRedis), Duration.ofMinutes(10));
            }
            if (fromRedis != null) local.put(id, fromRedis);
            return fromRedis;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    public void invalidate(Long id) {
        local.invalidate(id);
        redis.delete("sale:program:" + id);
    }

    private Program read(String value) {
        if (value == null) return null;
        try { return objectMapper.readValue(value, Program.class); }
        catch (JsonProcessingException e) { return null; }
    }

    private String write(Program value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("节目缓存序列化失败", e); }
    }
}
