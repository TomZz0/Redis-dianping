package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 实现秒杀优惠券功能
     * @param voucherId
     * @return
     */
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //1 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2 判断秒杀是否开始 没开始直接返回false
        if(LocalDateTime.now().isBefore(voucher.getBeginTime())){
            return Result.fail("秒杀尚未开始");
        }

        //3 判断秒杀是否结束
        if(LocalDateTime.now().isAfter(voucher.getEndTime())){
            return Result.fail("秒杀已经结束");
        }
        //4 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("晚了一步，优惠券被抢光了!");
        }
        //5 库存扣减
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id",voucherId).update();
        if (!success) return Result.fail("抢券失败,库存不足");
        //6 创建订单
        VoucherOrder order = new VoucherOrder();
        //6.1订单id
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        //6.2用户id
        Long userId = UserHolder.getUser().getId();
        order.setUserId(userId);
        //6.2秒杀券id
        order.setVoucherId(voucherId);
        //7 写入数据库
        save(order);
        //8 返回订单id
        return Result.ok(orderId);
    }
}
