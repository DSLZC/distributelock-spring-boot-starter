# distributelock-spring-boot-starter
spring boot 分布式锁starter，基于redis和zookeeper实现，接入使用可任选其一
> 如有建议或意见欢迎提issues

## 概述
我们在实际的接口或者业务开发中，不管是服务器单点还是服务器集群，都会有分布式锁的使用场景。
比如最常见的接口重复提交（业务重复处理）、商品超卖等问题，通用的解决方案就是本文所使用的“分布式锁”，
在同一个业务中，其中一个请求获取到锁之后，其他请求只有在获取到锁的请求释放锁（或者锁失效）之后才能继续“争抢”锁，
没有获得锁的请求是没有执行业务的权限的。

## 方案论证
这里我们主要讨论两种方案：基于redis的分布式锁和基于zookeeper的分布式锁

##### 基于redis的分布式锁
redis自身就提供了命令：SET key value NX PX expireTimeMs，专门用于分布式锁的场景，效率高且提供锁失效机制，
即使由于某种情况客户端没有发送解锁请求，也不会造成死锁。

但是如果redis跑在集群的情况下，由于redsi集群之间采用异步的方式进行数据的同步，
因此在并发量大的情况下有可能遇到数据同步不及时造成多个请求同时获取到锁，
虽然业界有redlock算法以及redisson客户端实现能基本处理此类问题，但是其算法逻辑实现很复杂，
并不能完美解决这个问题，更有甚者有分布式的专家Martin写了一篇文章[《How to do distributed locking》](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html),
质疑 Redlock 的正确性。Martin最后对Redlock算法的形容是： neither fish nor fowl （非驴非马）。
本人觉得这篇文章（[《基于Redis的分布式锁真的安全吗？》<http://www.sohu.com/a/128396689_487514>]）就redis集群分布式锁的安全问题就讲得非常好。

结论：
* 优点：性能好
* 缺点：存在集群数据同步不及时问题；锁失效时间不好控制

因此，要想使用redis分布式锁，最好使用redis单点模式，但是没有人能保证redis单点的高可用性。

##### 基于zookeeper的分布式锁
zookeeper是一个分布式的，开放源码的分布式应用程序协调服务，是一个为分布式应用提供一致性服务的软件，
提供的功能包括：配置维护、域名服务、分布式同步、组服务等。zookeeper机制规定同一个节点下只能有一个唯一名称的节点，
zookeeper上的一个znode看作是一把锁，所有客户端都去create同一个znode，最终成功创建的那个客户端也即拥有了这把锁。
zookeeper节点有两大类型：持久化节点和临时节点，客户端创建一个临时节点，当此客户端与zookeeper server断开后，该临时节点会自动删除。
由于zookeeper本身就强一致性的实现机制，因此不存在数据不一致的问题。

zookeeper提供了原生的API方式操作zookeeper，因为这个原生API使用起来并不是让人很舒服，于是出现了zkclient这种方式，以至到后来出现了Curator框架，
Curator对zkclient做了进一步的封装，让人使用zookeeper更加方便。有一句话，Guava is to JAVA what Curator is to Zookeeper。
Curator实现zookeeper分布式锁的基本原理如下：
* 在zookeeper指定节点（${serviceLockName}）下创建临时顺序节点node_n
* 获取${serviceLockName}下所有子节点children
* 对子节点按节点自增序号从小到大排序
* 判断本节点是不是第一个子节点，若是，则获取锁；若不是，则监听比该节点小的那个节点的删除事件
* 若监听事件生效，则回到第二步重新进行判断，直到获取到锁
* 若超过等待时间，则获取锁失败

就上面的Curator对分布式锁实现的算法还是挺复杂的，效率也不是太高，因为创建节点、获取所有子节点并排序等涉及到多个网络IO以及代码处理，所以效率上回打折扣，
还有释放锁的时候只会删除children节点，并不会删除${serviceLockName}节点，因此zookeeper server中有可能会出现大量的${serviceLockName}节点占用内存空间和Watcher。

因此，本人觉得Curator有些过于复杂了，可以直接利用zookeeper的特性（一个节点下只能有一个唯一名称的节点，客户端创建一个临时节点，当此客户端与zookeeper server断开后，该临时节点会自动删除），
重复创建子节点会抛出KeeperException.NodeExistsException（节点已存在异常）来实现zookeeper分布式锁。

结论：
* 优点：不存在数据不一致问题；有效的解决单点问题；锁有效时间控制灵活
* 缺点：性能稍差，因为每次在创建锁和释放锁的过程中，都要动态创建、销毁临时节点来实现锁功能。并且创建和删除节点只能通过Leader服务器来执行，然后将数据同步到其他机器上。

因此，本文强烈推荐使用zookeeper来实现分布式锁，但是又会多引入组件，为项目增加了风险。

## 使用方法

1. 分布式锁jar包引用
```
<dependency>
   <groupId>cn.dslcode</groupId>
   <artifactId>distributelock-spring-boot-starter</artifactId>
   <version>1.0.0</version>
</dependency>
```

2. spring-data-redis或zookeeperjar包引用
```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```
或
```
<dependency>
    <groupId>org.apache.zookeeper</groupId>
    <artifactId>zookeeper</artifactId>
    <version>${zookeeper.version}</version>
    <exclusions>
        <exclusion>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-log4j12</artifactId>
        </exclusion>
        <exclusion>
            <groupId>log4j</groupId>
            <artifactId>log4j</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

3. 配置参数
```
# 分布式锁方式：redis或zookeeper
distributelock.type=zookeeper
# 使用redis分布式锁，配置redis连接
spring.redis.host=127.0.0.1
spring.redis.port=6379
# 使用zookeeper分布式锁，配置zookeeper连接
distributelock.zookeeper.connect-string=127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183
```

4. 在需要进行分布式锁控制的方法添加@Lockable注解，注解字段如下
```
public @interface Lockable {

    /** lock key前缀，每一个业务一个key */
    String key();

    /** 等待时间/毫秒 */
    int waitTimeMs() default 0;

    /** 锁过期时间/毫秒，只对redis有效 */
    int timeoutMs() default 5000;

    /**
     * 方法参数field名称，支持多级，如：方法参数名或方法参数名.对象名.对象名。
     * 利用反射取值，用于和key组合起来组成lock名称
     */
    String[] fields() default {};

    /** 获取锁失败提示消息，可将此消息抛出RuntimeException，然后用全局异常处理器处理 */
    String failMsg() default "请勿重复提交|2101";

}
```

## 使用实例

 1. 使用注解的方式，starter已配置AOP自动拦截带有该注解的方法
 ```
 @PostMapping("createOrder")
 @Lockable(key = "order.addOder", waitTimeMs = 5000, timeoutMs = 5000, fields = {"product.id", "token"})
 public RestResponse createOrder(@RequestBody Product product, @RequestParam(name = "token") String token) {
     // TODO createOrder
     return RestResponse.success();
 }

 @Transactional
 @Lockable(key = "product.minusStock", waitTimeMs = 5000, timeoutMs = 5000, fields = "product.id")
 public void minusStock(Product product) {
     // TODO 商品扣减库存

 }
 ```

2. 不使用注解，直接使用DistributeLock.tryLock和DistributeLock.releaseLock方法。注意释放锁代码必须要在获得锁的情况下才能执行，并且需要用try finally，如下：
 ```
@Transactional
public void minusStock(Product product) {
    // TODO 商品扣减库存
    String lockValue = UUID.randomUUID().toString();
    boolean getLock = false;
    try {
        if (getLock = distributeLock.tryLock(lockKey, lockValue, waitTimeMs, timeoutMs)) {
            // TODO 获取锁成功，执行商品扣减库存业务逻辑

        }
        // 获取锁失败，执行失败业务逻辑
        if (!getLock) {
            throw new RuntimeException("当前操作用户过多，请稍后重试|2201");
        }
    } finally {
        // 获取锁成功才释放锁
        if (getLock) {
            distributeLock.releaseLock(lockKey, lockValue);
        }
    }
}
 ```

