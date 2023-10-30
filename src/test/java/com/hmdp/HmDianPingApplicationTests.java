package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl s;

    @Resource
    private CacheClient cacheClient;

    @Test
    void testSaveShop() throws InterruptedException {
        Shop byId = s.getById(1);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, byId, 10L, TimeUnit.SECONDS);
    }

}
