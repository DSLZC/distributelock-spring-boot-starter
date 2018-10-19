package cn.dslcode.distributelock;

import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author dongsilin
 * @version 2018/8/31.
 * 分布式锁自动配置
 */
@Slf4j
@Configuration
public class DistributeLockAutoConfiguration {

    @Value("${distributelock.type}")
    private String lockType;

    @PostConstruct
    public void checkConfig() {
        if (lockType == null || lockType.length() == 0 || !("redis".equals(lockType) || "zookeeper".equals(lockType))) {
            log.warn("********** 请配置分布式锁实现方式：distributelock.type = redis 或 distributelock.type = zookeeper");
            System.exit(1);
        }

        if ("redis".equals(lockType)) {
            try {
                Class.forName("org.springframework.data.redis.core.StringRedisTemplate");
            } catch (ClassNotFoundException e) {
                log.warn("********** 请导入spring-data-redis的jar包");
                System.exit(1);
            }
        }

        if ("zookeeper".equals(lockType)) {
            try {
                Class.forName("org.apache.zookeeper.ZooKeeper");
            } catch (ClassNotFoundException e) {
                log.warn("********** 请导入zookeeper的jar包");
                System.exit(1);
            }
        }
    }

    @Configuration
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(value = "distributelock.type", havingValue = "redis")
    public class RedisDistributeLockBean {
        @Bean
        @ConditionalOnMissingBean
        public DistributeLock distributeLock(StringRedisTemplate stringRedisTemplate){
            return new RedisDistributeLock(stringRedisTemplate);
        }
    }

    @Configuration
    @ConditionalOnClass(ZooKeeper.class)
    @ConditionalOnProperty(value = "distributelock.type", havingValue = "zookeeper")
    public class ZookeeperDistributeLockBean {
        @Bean
        @ConditionalOnMissingBean
        public DistributeLock distributeLock(@Value("${distributelock.zookeeper.connect-string}") String connectString){
            return new ZookeeperDistributeLock(connectString);
        }
    }




}