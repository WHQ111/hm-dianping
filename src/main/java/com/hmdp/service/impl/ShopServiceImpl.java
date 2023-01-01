package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.apache.ibatis.javassist.bytecode.analysis.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    StringRedisTemplate redisTemplate;



    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        //逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);

        return Result.ok(shop);

    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     *
     * @param id
     * @return
     * redis逻辑过期
     */
    public Shop queryWithLogicalExpire(Long id) {
        //1.从redis查询缓存
        String cache = redisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        if(StrUtil.isBlank(cache)) {
            //3.不存在直接返回
            return null;
        }
        //命中，需要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(cache, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            //未过期直接返回店铺信息
            return shop;
        }
        //过期 需要缓存重建
        //TODO 缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;

            //TODO 实现缓存重建
            //获取互斥锁
            Boolean islock = tryLock(lockKey);
            //获取成功
            if(islock) {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        this.saveDataToRedis(id, 20L);
                    } catch (Exception e) {
                        throw new RuntimeException();
                    } finally {
                        //释放锁
                        delLock(lockKey);
                    }
                });
            }

        return shop;
    }

    public Shop queryWithMutex(Long id) {
        //1.从redis查询缓存
        String cache = redisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        //3.存在直接返回
        if(StrUtil.isNotBlank(cache)) {
            Shop shop = JSONUtil.toBean(cache, Shop.class);
            return shop;
        }

        //判断是否是""
        if(cache != null) {
            return null;
        }
        Shop shop = null;
        String lockKey = "Lock:shop:" + id;
        try {
            //TODO 实现缓存重建
            //获取互斥锁
            Boolean islock = tryLock(lockKey);
            //获取失败
            if(!islock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //获取成功
            //4.不存在根据id査数据库
            shop = getById(id);
            //模拟延时
            Thread.sleep(200);

            //5.不存在返回
            if(shop == null || "".equals(shop)) {
                redisTemplate.opsForValue().set("cache:shop:" + id, "", 2, TimeUnit.MINUTES);
                return null;
            }
            //6.存在存到redis并返回
            redisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop), 30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //释放锁
            delLock(lockKey);
        }

        return shop;
    }


    public Shop queryWithPassThrough(Long id) {
        //1.从redis查询缓存
        String cache = redisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        //3.存在直接返回
        if(StrUtil.isNotBlank(cache)) {
            Shop shop = JSONUtil.toBean(cache, Shop.class);
            return shop;
        }

        //判断是否是""
        if(cache != null) {
            return null;
        }
        //4.不存在根据id査数据库
        Shop shop = getById(id);
        //5.不存在返回
        if(shop == null || "".equals(shop)) {
            redisTemplate.opsForValue().set("cache:shop:" + id, "", 2, TimeUnit.MINUTES);
            return null;
        }
        //6.存在存到redis并返回
        redisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop), 30, TimeUnit.MINUTES);
        return shop;
    }


    @Override
    @Transactional
    public Result updateCacheById(Shop shop) {

        if(shop.getId() == null) {
            return Result.fail("店铺id不为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        redisTemplate.delete("cache:shop:" + shop.getId());
        return Result.ok();
    }

    private Boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "", 30, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    private void delLock(String key) {
        redisTemplate.delete(key);
    }

    public void saveDataToRedis(Long id, Long expireSecond) throws InterruptedException {
        //查询店铺数据
        Shop shop = getById(id);
        //设置延迟时间
        Thread.sleep(200);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
        //写入redis
        redisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(redisData));
    }
}
