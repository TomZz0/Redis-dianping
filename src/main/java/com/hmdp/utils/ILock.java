package com.hmdp.utils;

public interface ILock {
    /**
     * 获取锁
     *
     * @param timeoutSec 锁的持续时间，超时后自动释放
     * @return 返回获取结果true OR false
     */
    boolean tryLock(int timeoutSec);

    void unlock();
}
