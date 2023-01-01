package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
             return Result.fail("手机号格式错误");
        }
        //校验验证码
        if(!RegexUtils.isCodeInvalid(loginForm.getCode()) || loginForm.getCode() == null) {
            return Result.fail("验证码格式错误");
        }

//        if(!loginForm.getCode().equals(session.getAttribute("code"))) {
//            return Result.fail("验证码错误");
//        }
        String cacheCode = stringRedisTemplate.opsForValue().get(SystemConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        if(!cacheCode.toString().equals(loginForm.getCode()) || cacheCode == null) {
            return Result.fail("验证码错误");
        }

        //根据手机号查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        //不存在创建用户
        if(user == null) {
            createUserWithPhone(loginForm.getPhone());
        }
        //保存用户信息到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //保存用户信息到redis
        //生成随机token
        String token = UUID.randomUUID().toString();
        //将User对象装换为map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName,fieldValue)->{
                    if (fieldValue == null){
                        fieldValue = "0";
                    }else {
                        fieldValue = fieldValue.toString();
                    }
                    return fieldValue;
                }));

        //存储
        stringRedisTemplate.opsForHash().putAll(SystemConstants.LOGIN_CODE_KEY + token, userMap);

        //设置token有效时间
        stringRedisTemplate.expire(SystemConstants.LOGIN_CODE_KEY + token, 20, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    //创建用户
    private User createUserWithPhone(String phone) {

        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }
}
