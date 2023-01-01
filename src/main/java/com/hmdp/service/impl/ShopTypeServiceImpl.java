package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Resource
    private IShopTypeService typeService;

    @Override
    public Result getTypeList() {
        String cache = redisTemplate.opsForValue().get("cache:shopTypes");

        if(cache != "" && cache != null) {
            List<ShopType> shopTypes = JSONUtil.toList(cache, ShopType.class);
            return Result.ok(shopTypes);
        }

        List<ShopType> shopTypes = typeService.query().orderByAsc("sort").list();

        if(shopTypes == null || "".equals(shopTypes)) {
            return Result.fail("数据库没有数据");
        }

        redisTemplate.opsForValue().set("cache:shopType", JSONUtil.toJsonStr(shopTypes));

        return Result.ok(shopTypes);
    }
}
