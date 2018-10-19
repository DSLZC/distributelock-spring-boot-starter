package cn.dslcode.distributelock;

/**
 * @author dongsilin
 * @version 2018/8/31.
 * 回调业务逻辑
 */
@FunctionalInterface
public interface CallBackExecutor<R> {

	R execute() throws Throwable;
}
