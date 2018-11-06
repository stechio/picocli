package picocli.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/** mock java.lang.reflect.Parameter (not available before Java 8) */
class MethodParam extends AccessibleObject {
    final Method method;
    final int paramIndex;
    final String name;
    int position;

    public MethodParam(Method method, int paramIndex) {
        this.method = method;
        this.paramIndex = paramIndex;
        String tmp = "arg" + paramIndex;
        try {
            Method getParameters = Method.class.getMethod("getParameters");
            Object parameters = getParameters.invoke(method);
            Object parameter = Array.get(parameters, paramIndex);
            tmp = (String) Class.forName("java.lang.reflect.Parameter")
                    .getDeclaredMethod("getName").invoke(parameter);
        } catch (Exception ignored) {
        }
        this.name = tmp;
    }

    public Type getParameterizedType() {
        return method.getGenericParameterTypes()[paramIndex];
    }

    public String getName() {
        return name;
    }

    public Class<?> getType() {
        return method.getParameterTypes()[paramIndex];
    }

    public Method getDeclaringExecutable() {
        return method;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        for (Annotation annotation : getDeclaredAnnotations()) {
            if (annotationClass.isAssignableFrom(annotation.getClass())) {
                return annotationClass.cast(annotation);
            }
        }
        return null;
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return method.getParameterAnnotations()[paramIndex];
    }

    @Override
    public void setAccessible(boolean flag) throws SecurityException {
        method.setAccessible(flag);
    }

    @Override
    public boolean isAccessible() throws SecurityException {
        return method.isAccessible();
    }

    @Override
    public String toString() {
        return method.toString() + ":" + getName();
    }
}
