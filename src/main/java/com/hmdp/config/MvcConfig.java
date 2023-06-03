package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author wzh
 * @date 2023年06月02日 12:39
 * Description:
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 添加拦截器
     *
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //拦截所有请求 刷新用户redis中存储的信息 按照顺序添加拦截器基本可以使得其拦截顺序确定 也可以设置order
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
        //判断用户是否存在 不存在就对特定路径拦截
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/hot",
                "/shop/**",
                "/shop-type/**",
                "/upload/**",
                "/voucher/**"
        ).order(1);
    }
}
