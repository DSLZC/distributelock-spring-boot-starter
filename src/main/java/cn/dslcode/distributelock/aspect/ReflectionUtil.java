package cn.dslcode.distributelock.aspect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author dongsilin
 * @version 2018/10/19.
 * 反射工具类
 */
class ReflectionUtil {

    /**
     * 改变private/protected的成员变量为可访问，尽量不进行改变，避免JDK的SecurityManager抱怨。
     */
    private static void makeAccessible(Field field) {
        if (!field.isAccessible() && (!Modifier.isPublic(field.getModifiers())
            || !Modifier.isPublic(field.getDeclaringClass().getModifiers())
            || Modifier.isFinal(field.getModifiers()))) {
            field.setAccessible(true);
        }
    }

    /**
     * 循环向上转型, 获取对象的DeclaredField, 并强制设置为可访问.
     * 如向上转型到Object仍无法找到, 返回null.
     * 因为getFiled()不能获取父类的private属性, 因此采用循环向上的getDeclaredField();
     */
    private static Field getField(final Class clazz, final String fieldName) throws NoSuchFieldException {
        for (Class<?> superClass = clazz; superClass != Object.class; superClass = superClass.getSuperclass()) {
            Field field = superClass.getDeclaredField(fieldName);
            makeAccessible(field);
            return field;
        }
        return null;
    }

    /**
     * 直接读取对象属性值, 无视private/protected修饰符, 不经过getter函数.
     * 性能较差, 用于单次调用的场景
     */
    protected static <T> T getFieldValue(Object obj, String fieldName) throws IllegalAccessException, NoSuchFieldException {
        if (fieldName.contains(".")) {
            String[] fields = fieldName.split(".");
            for (String field : fields) {
                obj = getFieldValue(obj, field);
            }
            return (T) obj;
        }

        Field field = getField(obj.getClass(), fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Could not find field [" + fieldName + "] on target [" + obj + ']');
        }
        return (T) field.get(obj);
    }


}
