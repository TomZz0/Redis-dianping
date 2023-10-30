package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.aop.ThrowsAdvice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //  Shop shop = queryWithPassThrough(id);
        //  Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_NULL_TTL, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存穿透
        //Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 10L, TimeUnit.SECONDS);
        if (shop == null) return Result.fail("店铺Id不存在");
        else return Result.ok(shop);
    }

    //启动线程的工具
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param id
     * @return
     */
    private Shop queryWithLogicalExpire(Long id) {
        //解决缓存穿透
        String key = CACHE_SHOP_KEY + id;
        //1从redis中查询缓存是否存在
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        //2如果未命中 直接返回 实际上逻辑过期处理方式不会未命中 因为数据持续存在直到人为删除
        if (StrUtil.isBlank(shopJSON)) {
            //3存在 直接返回
            return null;
        }
        //4命中 先把JSON字符串反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJSON, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //5 判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.1 未过期 直接返回
        if (LocalDateTime.now().isBefore(expireTime)) {
            return shop;
        }
        //5.2 已经过期 尝试进行缓存重建

        //5.2.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        //若成功 开启线程重建缓存
        if (tryLock(lockKey)) {
            //二次检测是否过期
            if (LocalDateTime.now().isBefore(expireTime)) {
                return shop;
            }
            //若已过期 启动线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    this.saveShopToRedis(id, LOCK_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //无论成功失败 都返回数据
        return shop;
    }

    /**
     * 互斥锁解决缓存击穿
     *
     * @param id
     * @return
     */
    private Shop queryWithMutex(Long id) {
        //互斥锁解决缓存击穿
        String key = CACHE_SHOP_KEY + id;
        //1从redis中查询缓存是否存在
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        //2判断是否是有效值 其中不包括空值
        if (StrUtil.isNotBlank(shopJSON)) {
            //3存在 直接返回
            return JSONUtil.toBean(shopJSON, Shop.class);
        }
        //存在且为空值
        if (shopJSON != null) {
            //此时为空字符串
            return null;
        }
        //实现缓存重建
        //4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean ifLock = tryLock(lockKey);
            //4.2判断是否获取成功
            //4.3若失败则休眠并重试
            if (!ifLock) {
                //休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4若成功 再次检测是否存在根据id查询数据库
            shopJSON = stringRedisTemplate.opsForValue().get(key);
            //2判断是否是有效值 其中不包括空值
            if (StrUtil.isNotBlank(shopJSON)) {
                //存在 直接返回
                return JSONUtil.toBean(shopJSON, Shop.class);
            }
            //查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            if (shop == null) {
                //5未查询到 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6存在 写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //7 释放🔒
            unLock(lockKey);
        }

        //8 返回
        return shop;
    }

    /**
     * 解决缓存穿透问题
     *
     * @param id
     * @return
     */
    private Shop queryWithPassThrough(Long id) {
        //解决缓存穿透
        String key = CACHE_SHOP_KEY + id;
        //1从redis中查询缓存是否存在
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        //2判断是否是有效值 其中不包括空值
        if (StrUtil.isNotBlank(shopJSON)) {
            //3存在 直接返回
            return JSONUtil.toBean(shopJSON, Shop.class);
        }
        //存在且为空值
        if (shopJSON != null) {
            //此时为空字符串
            return null;
        }
        //4不存在 查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            //5未查询到 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6存在 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    @Override
    @Transactional //事务处理 如有异常 会回滚
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("商铺ID有误");
        }
        //1 更新数据库
        updateById(shop);
        //2 删除缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    /**
     * 获取锁 使用到redis的setnx方法,相当于加锁
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

    public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
        //1,查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2 封装data对象
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
