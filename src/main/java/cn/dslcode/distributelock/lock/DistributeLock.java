package cn.dslcode.distributelock.lock;

import cn.dslcode.distributelock.CallBackExecutor;

/**
 * @author dongsilin
 * @version 2018/10/19.
 * 分布式锁
 */
public interface DistributeLock<R> {

    /**
     * 尝试加锁并回调业务逻辑
     * @param lockKey 锁key,每个业务一个key
     * @param waitTimeMs 等待时间/ms
     * @param timeoutMs 锁过期时间/ms，只对redis有效，zookeeper断开连接会自动删除
     * @param successExecutor 获取锁成功回调业务逻辑
     * @param failExecutor 获取锁失败回调业务逻辑
     * @return R 回调业务逻辑泛型
     * @throws Exception
     */
    R tryLockAndCallBack(String lockKey, int waitTimeMs, int timeoutMs, CallBackExecutor<R> successExecutor, CallBackExecutor<R> failExecutor) throws Throwable;

    /**
     * 尝试加锁
     * @param lockKey 锁key,每个业务一个key
     * @param lockValue 对应该锁的value，删除锁的时候会比对该value，只对redis有效
     * @param waitTimeMs 等待时间/ms
     * @param timeoutMs 锁过期时间/ms，只对redis有效，zookeeper断开连接会自动删除
     * @return boolean 是否获取成功
     * @throws Exception
     */
    boolean tryLock(String lockKey, String lockValue, int waitTimeMs, int timeoutMs) throws Exception;

    /**
     * 释放锁
     * @param lockKey 锁key,每个业务一个key
     * @param lockValue 对应该锁的value，删除锁的时候会比对该value，只对redis有效
     */
    void releaseLock(String lockKey, String lockValue) throws Exception;
}
