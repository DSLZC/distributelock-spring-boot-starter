package cn.dslcode.distributelock.aspect;

import cn.dslcode.distributelock.Lockable;
import cn.dslcode.distributelock.lock.DistributeLock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * @author dongsilin
 * @version 2018/10/19.
 */
@Slf4j
@Order(100)
@Aspect
@Configuration
public class DistributedLockAspect {

    @Autowired
    private DistributeLock distributeLock;

    @Pointcut("@annotation(cn.dslcode.distributelock.Lockable)")
    public void lockPointcut() {
    }

    @Around("lockPointcut()")
    public Object lockAround(ProceedingJoinPoint joinPoint) throws Throwable {
        if (log.isDebugEnabled()) log.debug("===========> DistributedLockAspect  begin ............");
        // 获取注解
        Lockable lockable = ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(Lockable.class);
        // 获取lockKey
        String lockKey = getLockKey(lockable, joinPoint);
        // 尝试加锁并回调业务逻辑
        return distributeLock.tryLockAndCallBack(
            lockKey,
            lockable.waitTimeMs(),
            lockable.timeoutMs(),
            () -> joinPoint.proceed(),
            () -> {
                throw new RuntimeException(lockable.failMsg());
            }
        );
    }

    /**
     * 根据fields 反射获取lockKey
     * @param lockable
     * @param joinPoint
     * @return lockKey
     */
    private String getLockKey(Lockable lockable, JoinPoint joinPoint) throws NoSuchFieldException, IllegalAccessException {
        String lockKey = lockable.key();
        if (lockable.fields().length > 0) {
            // 方法参数值
            Object[] argValues = joinPoint.getArgs();
            // 方法参数名称
            String[] argNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
            int argLength = argNames.length;
            // 循环拼接
            StringBuilder lockKeyBuilder = new StringBuilder(lockKey);
            for (String field : lockable.fields()) {
                lockKeyBuilder.append("_");
                int idx;
                if (field.contains(".")) {
                    idx = field.indexOf(".");
                    String an = field.substring(0, idx);
                    String av = field.substring(idx + 1);
                    for (idx = 0; idx < argLength; idx++) {
                        if (argNames[idx].equals(an)) {
                            Object fieldValue = ReflectionUtil.getFieldValue(argValues[idx], av);
                            lockKeyBuilder.append(fieldValue);
                            break;
                        }
                    }
                } else {
                    for (idx = 0; idx < argLength; idx++) {
                        if (argNames[idx].equals(field)) {
                            lockKeyBuilder.append(argValues[idx]);
                            break;
                        }
                    }
                }
            }
            lockKey = lockKeyBuilder.toString();
        }
        return lockKey;
    }
}
