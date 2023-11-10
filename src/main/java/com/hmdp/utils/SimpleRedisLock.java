package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author wzh
 * @date 2023年11月10日 22:47
 * Description:
 */
public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    @Override
    public boolean tryLock(int timeoutSec) {
        //获取key
        String key =  KEY_PREFIX + name;
        //获取value 线程id
        String id = ID_PREFIX + Thread.currentThread().getId();
        //防止自动拆箱产生空指针 这里使用Boolean.TRUE.equals
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, id, timeoutSec, TimeUnit.SECONDS));
    }

    @Override
    public void unlock() {
        //获取线程标识
        String lockID = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //判断是否一致
        String curID = ID_PREFIX + Thread.currentThread().getId();
        //如果一致才释放锁
        if (curID.equals(lockID)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
