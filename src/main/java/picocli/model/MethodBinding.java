package picocli.model;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import picocli.CommandLine;
import picocli.except.ParameterException;
import picocli.except.PicocliException;

public class MethodBinding implements IGetter, ISetter {
    private final Object scope;
    private final Method method;
    private Object currentValue;
    CommandLine commandLine;

    MethodBinding(Object scope, Method method) {
        this.scope = scope;
        this.method = method;
    }

    @SuppressWarnings("unchecked")
    public <T> T get() {
        return (T) currentValue;
    }

    public <T> T set(T value) throws PicocliException {
        try {
            @SuppressWarnings("unchecked")
            T result = (T) currentValue;
            method.invoke(scope, value);
            currentValue = value;
            return result;
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof PicocliException) {
                throw (PicocliException) ex.getCause();
            }
            throw new ParameterException(commandLine,
                    "Could not invoke " + method + " with " + value, ex.getCause());
        } catch (Exception ex) {
            throw new ParameterException(commandLine,
                    "Could not invoke " + method + " with " + value, ex);
        }
    }
}
