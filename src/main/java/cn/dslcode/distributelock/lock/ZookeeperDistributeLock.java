package cn.dslcode.distributelock.lock;

import cn.dslcode.distributelock.CallBackExecutor;
import java.io.Closeable;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

/**
 * @author dongsilin
 * @version 2018/10/19.
 * zookeeper分布式锁
 */
@Slf4j
public class ZookeeperDistributeLock<R> implements DistributeLock<R>, Closeable {

    /** zookeeper连接 */
    private ZooKeeper zooKeeperClient;
    /** session超时时间 */
    private int SESSION_TIMEOUT_MS = 10000;
    /** 分布式锁根节点 */
    private String ROOT_LOCK = "/locks";
    private String ROOT_LOCK_ = "/locks/";

    /**
     * 创建zookeeper连接并初始化分布式锁根节点
     * @param connectString list of server address: ip:port, ip:port, ip:port
     */
    public ZookeeperDistributeLock(String connectString) {
        try {
            // 初始化zookeeper连接
            this.zooKeeperClient = new ZooKeeper(connectString, SESSION_TIMEOUT_MS,null);
            // 如果分布式锁根节点，则创建根节点
            if (this.zooKeeperClient.exists(ROOT_LOCK, false) == null) {
                this.zooKeeperClient.create(ROOT_LOCK, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (Exception e) {
            log.error("", e);
            System.exit(1);
        }
    }

    /**
     * 尝试加锁并回调业务逻辑
     * @param lockKey 锁key,每个业务一个key
     * @param waitTimeMs 等待时间/ms
     * @param successExecutor 获取锁成功回调业务逻辑
     * @param failExecutor 获取锁失败回调业务逻辑
     * @return R 回调业务逻辑泛型
     * @throws Exception
     */
    @Override
    public R tryLockAndCallBack(String lockKey, int waitTimeMs, int timeoutMs, CallBackExecutor<R> successExecutor, CallBackExecutor<R> failExecutor) throws Throwable {
        boolean getLock = false;
        try {
            // 尝试获取锁
            if (getLock = tryLock(lockKey, null, waitTimeMs, 0)) {
                if (log.isDebugEnabled()) log.debug("ThreadName = {}, tryLock = {}", Thread.currentThread().getName(), "获取锁成功");
                // 获取锁成功，执行成功业务逻辑
                return successExecutor.execute();
            }
        } finally {
            if (getLock) {
                // 释放锁
                releaseLock(lockKey, null);
            }
        }
        if (log.isDebugEnabled()) log.debug("ThreadName = {}, tryLock = {}", Thread.currentThread().getName(), "获取锁失败");
        // 获取锁失败，执行失败业务逻辑
        return failExecutor.execute();
    }

    /**
     * 尝试加锁
     * @param lockKey 锁key,每个业务一个key
     * @param waitTimeMs 等待时间/ms
     * @return boolean 是否获取成功
     * @throws Exception
     */
    @Override
    public boolean tryLock(String lockKey, String lockValue, int waitTimeMs, int timeoutMs) throws Exception {
        lockKey = ROOT_LOCK_ + lockKey;
        boolean getLock;
        // 尝试获取锁
        // 创建临时节点，如果节点已经存在，会抛出 KeeperException.NodeExistsException
        if (!(getLock = createTempNode(lockKey)) && waitTimeMs > 0) {
            long startTime = System.currentTimeMillis();
            int yieldTimes = 0;// 让出CPU次数
            do {
                if (log.isDebugEnabled()) log.debug("ThreadName = {}, tryLock = {}", Thread.currentThread().getName(), "等待获取.............");
                Thread.yield();// 让出CPU
                if (getLock = createTempNode(lockKey)) {
                    break;
                }
                // 还是抢不到，睡一会
                if(yieldTimes++ >= 2 ) {
                    Thread.sleep(20 + 10*yieldTimes);
                }
            } while (System.currentTimeMillis() - startTime < waitTimeMs);
        }
        return getLock;
    }

    /**
     * 释放锁
     * @param lockKey 锁key,每个业务一个key
     */
    @Override
    public void releaseLock(String lockKey, String lockValue) throws Exception {
        // 释放锁，删除节点
        deleteNode(ROOT_LOCK_ + lockKey);
    }


    /******************************* zooKeeper 节点操作逻辑 ******************************/

    /**
     * 创建临时节点，添加锁，如果节点已经存在，会抛出 KeeperException.NodeExistsException
     * @param nodeName
     * @return boolean
     * @throws InterruptedException
     */
    private boolean createTempNode(String nodeName) throws InterruptedException {
        try {
            // 创建临时节点，添加锁，如果节点已经存在，会抛出 KeeperException.NodeExistsException
            zooKeeperClient.create(nodeName, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            return true;
        } catch (KeeperException e) {
            // 非节点已存在异常则打印日志
            if (!(e instanceof KeeperException.NodeExistsException)) {
                log.error("", e);
            }
            // 获取锁失败
            return false;
        }
    }

    /**
     * 删除节点
     * @param nodeName 节点名称
     * @throws KeeperException
     * @throws InterruptedException
     */
    private void deleteNode(String nodeName) throws KeeperException, InterruptedException {
        try {
            zooKeeperClient.delete(nodeName, 0);
        } catch (KeeperException.NotEmptyException e) {
            // 有子节点，递归删除
            zooKeeperClient.getChildren(nodeName, false).parallelStream().forEach(child -> {
                try {
                    deleteNode(nodeName.concat("/").concat(child));
                } catch (Exception e1) {
                    log.error("", e1);
                }
            });
            // 子节点删除完成后，再删除自身
            zooKeeperClient.delete(nodeName, 0);
        }
    }


    /**
     * 关闭zooKeeper连接
     */
    @Override
    public void close() {
        if (zooKeeperClient != null) {
            try {
                zooKeeperClient.close();
            } catch (InterruptedException e) {
                log.error("", e);
            }
        }
    }

}
