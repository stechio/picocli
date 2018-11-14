package picocli.model;

import java.util.Objects;

public abstract class TypeConverter<T> implements ITypeConverter<T> {
    @Override
    public String viewOf(Object value) {
        return Objects.toString(value);
    }
}