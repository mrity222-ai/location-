package de.robv.android.xposed;

import java.lang.reflect.Method;

public class XposedHelpers {
    public static Method findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }
    public static Method findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }
    public static Object callMethod(Object obj, String methodName, Object... args) {
        return null;
    }
    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return null;
    }
    public static Object getObjectField(Object obj, String fieldName) {
        return null;
    }
}
