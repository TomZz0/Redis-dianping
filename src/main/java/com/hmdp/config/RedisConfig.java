package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author wzh
 * @date 2023年11月12日 22:08
 * Description:
 */
@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient() {
        //配置类
        Config config = new Config();
        //添加redis地址,也可以使用config.useClusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://192.168.184.130:6379");
        //创建
        return Redisson.create(config);
    }
}
