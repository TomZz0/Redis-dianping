package com.hmdp.utils;

import ch.qos.logback.classic.spi.EventArgUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author wzh
 * @date 2023年11月07日 21:44
 * Description:
 */
@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    private static final int BITS_OFFSET = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        //1生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long second = now.toEpochSecond(ZoneOffset.UTC);
        long stamp = second - BEGIN_TIMESTAMP;
        //2生成序列号
        //2.1 获取当前日期，精确到天
        String today = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //2.2自增长id
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + today);

        //3拼接并返回 左移后低位或
        return stamp << BITS_OFFSET | count;
    }

    /**
     * 计算起始时间秒数 以2022年开始
     *
     * @param args
     */
    public static void main(String[] args) {
        //生成起始时间
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        //转换为秒
        long seconds = time.toEpochSecond(ZoneOffset.UTC);

        System.out.println(seconds);
    }
}
