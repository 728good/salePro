package io.github.good728.sale.app.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.good728.sale.app.domain.Entities.Seat;
import io.github.good728.sale.app.mapper.SeatMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SeatInventoryService {
    private static final DefaultRedisScript<Long> INITIALIZE = new DefaultRedisScript<>("""
            local key=KEYS[1]
            if redis.call('EXISTS',key)==1 then
              local available=0
              local values=redis.call('HVALS',key)
              for _,v in ipairs(values) do
                if v=='0' then available=available+1 end
              end
              return available
            end
            for i=1,#ARGV,2 do redis.call('HSET',key,ARGV[i],ARGV[i+1]) end
            if #ARGV>0 then redis.call('EXPIRE',key,86400) end
            return #ARGV/2
            """, Long.class);
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local key=KEYS[1]
            for i=2,#ARGV do
              local v=redis.call('HGET',key,ARGV[i])
              if (not v) or v~='0' then return 0 end
            end
            for i=2,#ARGV do redis.call('HSET',key,ARGV[i],ARGV[1]) end
            redis.call('EXPIRE',key,86400)
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            local key=KEYS[1]
            local released=0
            for i=2,#ARGV do
              if redis.call('HGET',key,ARGV[i])==ARGV[1] then
                redis.call('HSET',key,ARGV[i],'0')
                released=released+1
              end
            end
            return released
            """, Long.class);
    private static final DefaultRedisScript<Long> SOLD = new DefaultRedisScript<>("""
            local key=KEYS[1]
            for i=2,#ARGV do
              if redis.call('HGET',key,ARGV[i])~=ARGV[1] then return 0 end
            end
            for i=2,#ARGV do redis.call('HSET',key,ARGV[i],'SOLD') end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final SeatMapper seatMapper;

    public SeatInventoryService(StringRedisTemplate redis, SeatMapper seatMapper) {
        this.redis = redis;
        this.seatMapper = seatMapper;
    }

    public int warm(Long programId) {
        List<Seat> seats = seatMapper.selectList(Wrappers.<Seat>lambdaQuery()
                .eq(Seat::getProgramId, programId).eq(Seat::getStatus, "AVAILABLE"));
        String key = key(programId);
        List<String> args = new ArrayList<>(seats.size() * 2);
        seats.forEach(seat -> {
            args.add(seat.getId().toString());
            args.add("0");
        });
        Long available = redis.execute(INITIALIZE, List.of(key), args.toArray());
        return available == null ? 0 : available.intValue();
    }

    public boolean reserve(Long programId, List<Long> seatIds, String owner) {
        if (seatIds == null || seatIds.isEmpty() || seatIds.size() > 20) return false;
        List<String> args = args(owner, seatIds);
        return Long.valueOf(1).equals(redis.execute(RESERVE, List.of(key(programId)), args.toArray()));
    }

    public long release(Long programId, List<Long> seatIds, String owner) {
        Long result = redis.execute(RELEASE, List.of(key(programId)), args(owner, seatIds).toArray());
        return result == null ? 0 : result;
    }

    public boolean markSold(Long programId, List<Long> seatIds, String owner) {
        return Long.valueOf(1).equals(redis.execute(SOLD, List.of(key(programId)), args(owner, seatIds).toArray()));
    }

    private List<String> args(String owner, List<Long> seatIds) {
        List<String> args = new ArrayList<>(seatIds.size() + 1);
        args.add(owner);
        seatIds.forEach(id -> args.add(id.toString()));
        return args;
    }

    private String key(Long programId) {
        return "sale:inventory:{" + programId + "}";
    }
}
