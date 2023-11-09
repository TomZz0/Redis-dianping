package com.hmdp.service.impl;

import cn.hutool.core.text.UnicodeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.apache.ibatis.javassist.tools.rmi.AppletServer;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
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
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2 判断秒杀是否开始 没开始直接返回false
        if (LocalDateTime.now().isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀尚未开始");
        }

        //3 判断秒杀是否结束
        if (LocalDateTime.now().isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀已经结束");
        }

        //4 判断库存是否充足
        Integer stock = voucher.getStock();
        if (stock < 1) {
            return Result.fail("晚了一步，优惠券被抢光了!");
        }
        Long userId = UserHolder.getUser().getId();
        //每次用户都是重新获得的，所以需要toString。而toString每次又是new的，还是不一致，所以使用intern常量池中的string
        //又因为事务提交要在方法结束后才进行，防止锁释放后事务还没提交而其他线程又进入产生问题，所以要在方法外加锁
        synchronized (userId.toString().intern()) {
            //获取事务有关的代理对象
            IVoucherOrderService service = (IVoucherOrderService) AopContext.currentProxy();

            return service.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //5一人一单 处理 查询order表中是否存在该id
        Long userId = UserHolder.getUser().getId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) return Result.fail("同一账户只能购买一张");


        //6 库存扣减
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) return Result.fail("抢券失败,库存不足");

        //7 创建订单
        VoucherOrder order = new VoucherOrder();
        //7.1订单id
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        //7.2用户id
        order.setUserId(userId);
        //7.2秒杀券id
        order.setVoucherId(voucherId);
        //8 写入数据库
        save(order);
        //9 返回订单id
        return Result.ok(orderId);
    }
}
