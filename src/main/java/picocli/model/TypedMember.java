package picocli.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

import org.apache.commons.lang3.StringUtils;

import picocli.annots.Mixin;
import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.annots.ParentCommand;
import picocli.annots.Spec;
import picocli.annots.Unmatched;
import picocli.excepts.InitializationException;
import picocli.util.Assert;
import picocli.util.ObjectUtilsExt;

class TypedMember {
    final AccessibleObject accessible;
    final String name;
    final Class<?> type;
    final Type genericType;
    final boolean hasInitialValue;
    private IGetter getter;
    private ISetter setter;

    TypedMember(Field field) {
        accessible = Assert.notNull(field, "field");
        accessible.setAccessible(true);
        name = field.getName();
        type = field.getType();
        genericType = field.getGenericType();
        hasInitialValue = true;
    }

    static TypedMember createIfAnnotated(Field field, Object scope) {
        return isAnnotated(field) ? new TypedMember(field, scope) : null;
    }

    private TypedMember(Field field, Object scope) {
        this(field);
        if (Proxy.isProxyClass(scope.getClass())) {
            throw new InitializationException("Invalid picocli annotation on interface field");
        }
        FieldBinding binding = new FieldBinding(scope, field);
        getter = binding;
        setter = binding;
    }

    static TypedMember createIfAnnotated(Method method, Object scope) {
        return isAnnotated(method) ? new TypedMember(method, scope) : null;
    }

    private TypedMember(Method method, Object scope) {
        accessible = Assert.notNull(method, "method");
        accessible.setAccessible(true);
        name = propertyName(method.getName());
        Class<?>[] parameterTypes = method.getParameterTypes();
        boolean isGetter = parameterTypes.length == 0 && method.getReturnType() != Void.TYPE
                && method.getReturnType() != Void.class;
        boolean isSetter = parameterTypes.length > 0;
        if (isSetter == isGetter) {
            throw new InitializationException(
                    "Invalid method, must be either getter or setter: " + method);
        }
        if (isGetter) {
            hasInitialValue = true;
            type = method.getReturnType();
            genericType = method.getGenericReturnType();
            if (Proxy.isProxyClass(scope.getClass())) {
                PicocliInvocationHandler handler = (PicocliInvocationHandler) Proxy
                        .getInvocationHandler(scope);
                PicocliInvocationHandler.ProxyBinding binding = handler.new ProxyBinding(method);
                getter = binding;
                setter = binding;
                initializeInitialValue(method);
            } else {
                //throw new IllegalArgumentException("Getter method but not a proxy: " + scope + ": " + method);
                MethodBinding binding = new MethodBinding(scope, method);
                getter = binding;
                setter = binding;
            }
        } else {
            hasInitialValue = false;
            type = parameterTypes[0];
            genericType = method.getGenericParameterTypes()[0];
            MethodBinding binding = new MethodBinding(scope, method);
            getter = binding;
            setter = binding;
        }
    }

    TypedMember(MethodParam param, Object scope) {
        accessible = Assert.notNull(param, "command method parameter");
        accessible.setAccessible(true);
        name = param.getName();
        type = param.getType();
        genericType = param.getParameterizedType();

        // bind parameter
        ObjectBinding binding = new ObjectBinding();
        getter = binding;
        setter = binding;
        hasInitialValue = initializeInitialValue(param);
    }

    private boolean initializeInitialValue(Object arg) {
        boolean initialized = true;
        try {
            if (type == Boolean.TYPE) {
                setter.set(false);
            } else if (type == Byte.TYPE) {
                setter.set(Byte.valueOf((byte) 0));
            } else if (type == Short.TYPE) {
                setter.set(Short.valueOf((short) 0));
            } else if (type == Integer.TYPE) {
                setter.set(Integer.valueOf(0));
            } else if (type == Long.TYPE) {
                setter.set(Long.valueOf(0L));
            } else if (type == Float.TYPE) {
                setter.set(Float.valueOf(0f));
            } else if (type == Double.TYPE) {
                setter.set(Double.valueOf(0d));
            } else {
                initialized = false;
            }
        } catch (

        Exception ex) {
            throw new InitializationException(
                    "Could not set initial value for " + arg + ": " + ex.toString(), ex);
        }
        return initialized;
    }

    static boolean isAnnotated(AnnotatedElement e) {
        return false || e.isAnnotationPresent(Option.class)
                || e.isAnnotationPresent(Parameters.class) || e.isAnnotationPresent(Unmatched.class)
                || e.isAnnotationPresent(Mixin.class) || e.isAnnotationPresent(Spec.class)
                || e.isAnnotationPresent(ParentCommand.class);
    }

    boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return accessible.isAnnotationPresent(annotationClass);
    }

    <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return accessible.getAnnotation(annotationClass);
    }

    String name() {
        return name;
    }

    boolean isArgSpec() {
        return isOption() || isParameter() || (isMethodParameter() && !isMixin());
    }

    boolean isOption() {
        return isAnnotationPresent(Option.class);
    }

    boolean isParameter() {
        return isAnnotationPresent(Parameters.class);
    }

    boolean isMixin() {
        return isAnnotationPresent(Mixin.class);
    }

    boolean isUnmatched() {
        return isAnnotationPresent(Unmatched.class);
    }

    boolean isInjectSpec() {
        return isAnnotationPresent(Spec.class);
    }

    boolean isMultiValue() {
        return ObjectUtilsExt.isGroup(getType());
    }

    IGetter getter() {
        return getter;
    }

    ISetter setter() {
        return setter;
    }

    Class<?> getType() {
        return type;
    }

    Type getGenericType() {
        return genericType;
    }

    public String toString() {
        return accessible.toString();
    }

    String toGenericString() {
        return accessible instanceof Field ? ((Field) accessible).toGenericString()
                : accessible instanceof Method ? ((Method) accessible).toGenericString()
                        : ((MethodParam) accessible).toString();
    }

    boolean isMethodParameter() {
        return accessible instanceof MethodParam;
    }

    String mixinName() {
        String annotationName = getAnnotation(Mixin.class).name();
        return StringUtils.isBlank(annotationName) ? name() : annotationName;
    }

    static String propertyName(String methodName) {
        if (methodName.length() > 3
                && (methodName.startsWith("get") || methodName.startsWith("set"))) {
            return decapitalize(methodName.substring(3));
        }
        return decapitalize(methodName);
    }

    private static String decapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
}
