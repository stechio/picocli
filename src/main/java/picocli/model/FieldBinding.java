package picocli.model;

import java.lang.reflect.Field;

import picocli.excepts.PicocliException;

class FieldBinding implements IGetter, ISetter {
    private final Object scope;
    private final Field field;

    FieldBinding(Object scope, Field field) {
        this.scope = scope;
        this.field = field;
    }

    public <T> T get() throws PicocliException {
        try {
            @SuppressWarnings("unchecked")
            T result = (T) field.get(scope);
            return result;
        } catch (Exception ex) {
            throw new PicocliException("Could not get value for field " + field, ex);
        }
    }

    public <T> T set(T value) throws PicocliException {
        try {
            @SuppressWarnings("unchecked")
            T result = (T) field.get(scope);
            field.set(scope, value);
            return result;
        } catch (Exception ex) {
            throw new PicocliException(
                    "Could not set value for field " + field + " to " + value, ex);
        }
    }
}
