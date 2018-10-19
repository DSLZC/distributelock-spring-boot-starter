package cn.dslcode.distributelock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * @author dongsilin
 * @version 2018/10/19.
 * redis分布式锁
 */
@Slf4j
public class RedisDistributeLock<R> implements DistributeLock<R> {

    /** redis连接 */
    private StringRedisTemplate redisTemplate;
    /** 分布式锁前缀 */
    private String lockPrefix = "lock:";

    /** 加锁Lua脚本 */
    private String luaLockScript = "return redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2])";
    /** 解锁Lua脚本 */
    private String luaDelLockScript = "if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]) end";
    /** 加锁脚本对象 */
    private RedisScript<String> redisLockScript = new DefaultRedisScript<>(luaLockScript, String.class);
    /** 解锁脚本对象 */
    private RedisScript<Void> redisDelLockScript = new DefaultRedisScript<>(luaDelLockScript, Void.class);

    /**
     * 创建zookeeper 连接并初始化分布式锁根节点
     * @param redisTemplate list of server address: ip:port, ip:port, ip:port
     */
    public RedisDistributeLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        try {
            redisTemplate.getConnectionFactory().getClusterConnection();
            log.warn("-------------- 请勿在redis cluster模式下运行分布式锁 ............");
            // 不支持cluster模式，有风险
            System.exit(1);
        } catch (Exception e) {
            log.info("-------------- redis 分布式锁运行在非cluster模式下，非常安全高效 ............");
        }
    }


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
    @Override
    public R tryLockAndCallBack(String lockKey, int waitTimeMs, int timeoutMs, CallBackExecutor<R> successExecutor, CallBackExecutor<R> failExecutor) throws Exception {
        String lockValue = UUID.randomUUID().toString();
        boolean getLock = false;
        try {
            if (getLock = tryLock(lockKey, lockValue, waitTimeMs, timeoutMs)) {
                log.debug("ThreadName = {}, tryAddLock = {}", Thread.currentThread().getName(), "获取锁成功");
                // 继续执行业务
                return successExecutor.execute();
            }
        } finally {
            // 释放锁
            if (getLock) {
                releaseLock(lockKey, lockValue);
            }
            log.debug("===========>DistributedRedisLockAspect  end ............");
        }
        // 获取锁失败
        return failExecutor.execute();
    }


    /**
     * 尝试加锁
     * @param lockKey 锁key,每个业务一个key
     * @param lockValue 对应该锁的value，删除锁的时候会比对该value，只对redis有效
     * @param waitTimeMs 等待时间/ms
     * @param timeoutMs 锁过期时间/ms，只对redis有效，zookeeper断开连接会自动删除
     * @return boolean 是否获取成功
     * @throws Exception
     */
    @Override
    public boolean tryLock(String lockKey, String lockValue, int waitTimeMs, int timeoutMs) throws Exception {
        lockKey = lockPrefix + lockKey;
        boolean getLock;
        // 没有获得锁并且等待时间大于0，进入等待时间循环获取锁
        if (!(getLock = redisAddLock(lockKey, lockValue, timeoutMs)) && waitTimeMs > 0) {
            long startTime = System.currentTimeMillis();
            do {
                Thread.sleep(100);
                Thread.yield();// 让出CPU
                log.info("ThreadName = {}, tryAddLock = {}", Thread.currentThread().getName(), "等待获取.............");
                // 如果获得锁，直接跳出循环
                if (getLock = redisAddLock(lockKey, lockValue, timeoutMs)) {
                    break;
                }
            } while (System.currentTimeMillis() - startTime < waitTimeMs);
        }
        return getLock;
    }

    /**
     * 释放锁
     * @param lockKey 锁key,每个业务一个key
     * @param lockValue 对应该锁的value，删除锁的时候会比对该value，只对redis有效
     */
    @Override
    public void releaseLock(String lockKey, String lockValue) {
        redisDelLock(lockPrefix + lockKey, lockValue);
    }


    /***************************** redis 操作逻辑 ******************************/


    /**
     * 尝试添加分布式锁
     * @param lockKey
     * @param lockValue
     * @param timeoutMs 锁过期时间/毫秒
     * @return true：加锁成功 fasle：加锁失败
     */
    private boolean redisAddLock(String lockKey, String lockValue, int timeoutMs) {
        List<String> keys = new ArrayList<>(1);
        keys.add(lockKey);
        String tryLock = redisTemplate.execute(redisLockScript, keys, lockValue, String.valueOf(timeoutMs));
        return "OK".equals(tryLock);
    }

    /**
     * 释放锁
     * @param lockKey
     * @param lockValue
     */
    private void redisDelLock(String lockKey, String lockValue) {
        List<String> keys = new ArrayList<>(1);
        keys.add(lockKey);
        redisTemplate.execute(redisDelLockScript, keys, lockValue);
    }

}
