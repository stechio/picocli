package picocli.model;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import picocli.util.Assert;

class PicocliInvocationHandler implements InvocationHandler {
    final Map<String, Object> map = new HashMap<String, Object>();

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return map.get(method.getName());
    }

    class ProxyBinding implements IGetter, ISetter {
        private final Method method;

        ProxyBinding(Method method) {
            this.method = Assert.notNull(method, "method");
        }

        @SuppressWarnings("unchecked")
        public <T> T get() {
            return (T) map.get(method.getName());
        }

        public <T> T set(T value) {
            T result = get();
            map.put(method.getName(), value);
            return result;
        }
    }
}
