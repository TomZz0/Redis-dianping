package com.hmdp.utils;

import cn.hutool.core.lang.func.Func;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author wzh
 * @date 2023年07月22日 17:39
 * Description:
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        //把 time个unit转成秒 加入到当前时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //转成JSON并写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     *
     * @param keyPrefix 存入redis数据的前缀
     * @param id 存入redis数据的id
     * @param type 返回值的类型
     * @param dbFallBack Function类 传入一段处理逻辑,一般是方法.其泛型分别表示输入参数,输入.当有多个输入时使用BiFunction,最后一个泛型表示输出
     * @param timeNull 如果为空,要将空值写入redis防止缓存穿透,表示空值存在时间
     * @param timeNotNull 表示非空值在缓存的存在时间
     * @param unit 时间单位
     * @return 返回通过缓存穿透方法查询的结果
     * @param <R> 返回类型
     * @param <ID> ID,因为不确定id的类型,采用泛型
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long timeNull, Long timeNotNull, TimeUnit unit) {
        //解决缓存穿透
        String key = keyPrefix + id;
        //1从redis中查询缓存是否存在
        String json = stringRedisTemplate.opsForValue().get(key);
        //2判断是否是有效值 其中不包括空值
        if (StrUtil.isNotBlank(json)) {
            //3存在 直接返回
            return JSONUtil.toBean(json, type);
        }
        //存在且为空值
        if (json != null) {
            //此时为空字符串
            return null;
        }
        //4不存在 查询数据库
        R r = dbFallBack.apply(id);
        if (r == null) {
            //5未查询到 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", timeNull, unit);
            return null;
        }
        //6存在 写入redis
        this.set(key, r, timeNotNull, unit);

        return r;
    }

    //启动线程的工具
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param id
     * @return
     */
    public  <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        //解决缓存穿透
        String key = keyPrefix + id;
        //1从redis中查询缓存是否存在
        String json = stringRedisTemplate.opsForValue().get(key);
        //2如果未命中 直接返回 实际上逻辑过期处理方式不会未命中 因为数据持续存在直到人为删除
        if (StrUtil.isBlank(json)) {
            //3存在 直接返回
            return null;
        }
        //4命中 先把JSON字符串反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //5 判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.1 未过期 直接返回
        if (LocalDateTime.now().isBefore(expireTime)) {
            return r;
        }
        //5.2 已经过期 尝试进行缓存重建

        //5.2.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        //若成功 开启线程重建缓存
        if (tryLock(lockKey)) {
            //二次检测是否过期
            if (LocalDateTime.now().isBefore(expireTime)) {
                return r;
            }
            //若已过期 启动线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    R r1 = dbFallBack.apply(id);
                    //写入redis
                    setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //无论成功失败 都返回数据
        return r;
    }

    /**
     * 获取锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
