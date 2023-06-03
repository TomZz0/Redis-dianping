package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2 如果不符合 返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3 符合则生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4 保存验证码到session
        //session.setAttribute("code", code);

        //4 保存到redis中 有效时间2分钟
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5 发送验证码
        log.debug("发送验证码成功,验证码为:" + code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //2 校验验证码 从redis中获取验证码
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);

        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        //3 根据手机号查询用户并判断是否存在
        User user = query().eq("phone", phone).one();

        if (user == null) {
            //3.1 用户不存在 创建新用户并保存到数据库 调用方法
            user = createUserWithPhone(phone);
        }

        //3.2 用户保存到redis
        //a 随即生成token 作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //b 将user对象转为hashmap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //因为stringRedisTemplate要求map的key value都要为string 类型 所以进行转换
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //c 存储
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //d 设置有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //4 返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        //初始化数据
        user.setCreateTime(LocalDateTime.now());
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setPhone(phone);
        //保存到数据库
        save(user);
        return user;
    }
}
