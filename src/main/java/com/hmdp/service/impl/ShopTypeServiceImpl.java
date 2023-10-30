package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getList() {
        //查询是否存在
        List<String> shops = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        //若存在 转换类型后返回
        if (shops != null && shops.size()!=0) {
            List<ShopType> shopTypes = new LinkedList<>();
            for (String s : shops) {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                shopTypes.add(shopType);
            }
            return Result.ok(shopTypes);
        }
        //不存在 先查询数据库
        List<ShopType> list = query().orderByAsc("sort").list();
        if (list == null) {
            return Result.fail("店铺不存在");
        }
        //数据库中查到数据 先存到redis中 再返回
        for (ShopType shopType : list) {
            String s = JSONUtil.toJsonStr(shopType);
            stringRedisTemplate.opsForList().leftPush(RedisConstants.CACHE_SHOP_TYPE_KEY, s);
        }
        return Result.ok(list);
    }
}
