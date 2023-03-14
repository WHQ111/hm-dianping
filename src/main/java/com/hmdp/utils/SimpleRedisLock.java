package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "";
    private static final DefaultRedisScript<Long> REDIS_SCRIPT;

    static {
        REDIS_SCRIPT = new DefaultRedisScript<Long>();
        REDIS_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        REDIS_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        //获取线程标识
        String threadId = Thread.currentThread().getId() + ID_PREFIX;
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        //调用lua脚本
        stringRedisTemplate.execute(REDIS_SCRIPT, Collections.singletonList(KEY_PREFIX + name),
                Thread.currentThread().getId()+ ID_PREFIX);
    }

//    @Override
//    public void unLock() {
//        //获取当前锁的id
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //获取线程标识
//        String threadId = Thread.currentThread().getId + ID_PREFIX;
//        if(id.equals(threadId)) {
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
