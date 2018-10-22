package cn.dslcode.distributelock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author dongsilin
 * @version 2018/4/19.
 * 分布式锁注解
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Lockable {


		/** lock key前缀 */
		String key();

		/** 等待时间/毫秒 */
		int waitTimeMs() default 0;

		/** 锁过期时间/毫秒，只对redis有效 */
		int timeoutMs() default 5000;

		/**
		 * 方法参数field名称，支持多级，如：方法参数名 或 方法参数名.对象名.对象名。
		 * 利用反射取值，用于和key组合起来组成新的lockKey
		 */
		String[] fields() default {};

		/** 获取锁失败提示消息，可将此消息抛出RuntimeException，然后用全局异常处理器处理 */
		String failMsg() default "请勿重复提交|2101";

}
