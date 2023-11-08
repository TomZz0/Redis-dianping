package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl s;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;
    /**
     * 线程池
     */
    private ExecutorService es = Executors.newFixedThreadPool(500);


    @Test
    void testIdWorker() throws InterruptedException {
        //设置线程检测全局id生成器
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable tasks = () -> {
            for (int i = 0; i < 100; i++) {
                long test = redisIdWorker.nextId("test");
                System.out.println("id=" + test);
            }
            countDownLatch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) es.submit(tasks);
        long end = System.currentTimeMillis();
        countDownLatch.await();
        System.out.println(end - start  );
    }

    @Test
    void testSaveShop() throws InterruptedException {
        Shop byId = s.getById(1);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, byId, 10L, TimeUnit.SECONDS);
    }

}
