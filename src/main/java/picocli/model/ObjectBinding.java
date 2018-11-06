package picocli.model;

public class ObjectBinding implements IGetter, ISetter {
    private Object value;

    @SuppressWarnings("unchecked")
    public <T> T get() {
        return (T) value;
    }

    public <T> T set(T value) {
        T result = value;
        this.value = value;
        return result;
    }
}
