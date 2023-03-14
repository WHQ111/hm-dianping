package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取锁
     * true 成功
     */

    boolean tryLock(Long timeoutSec);

    void unLock();

}
