package picocli.model;

import java.util.Arrays;
import java.util.Collection;

import picocli.excepts.PicocliException;
import picocli.util.Assert;

/**
 * This class allows applications to specify a custom binding that will be invoked for
 * unmatched arguments. A binding can be created with a {@code ISetter} that consumes the
 * unmatched arguments {@code String[]}, or with a {@code IGetter} that produces a
 * {@code Collection<String>} that the unmatched arguments can be added to.
 * 
 * @since 3.0
 */
public class UnmatchedArgsBinding {
    private final IGetter getter;
    private final ISetter setter;

    /**
     * Creates a {@code UnmatchedArgsBinding} for a setter that consumes {@code String[]}
     * objects.
     * 
     * @param setter
     *            consumes the String[] array with unmatched arguments.
     */
    public static UnmatchedArgsBinding forStringArrayConsumer(ISetter setter) {
        return new UnmatchedArgsBinding(null, setter);
    }

    /**
     * Creates a {@code UnmatchedArgsBinding} for a getter that produces a
     * {@code Collection<String>} that the unmatched arguments can be added to.
     * 
     * @param getter
     *            supplies a {@code Collection<String>} that the unmatched arguments can be
     *            added to.
     */
    public static UnmatchedArgsBinding forStringCollectionSupplier(IGetter getter) {
        return new UnmatchedArgsBinding(getter, null);
    }

    private UnmatchedArgsBinding(IGetter getter, ISetter setter) {
        if (getter == null && setter == null) {
            throw new IllegalArgumentException("Getter and setter cannot both be null");
        }
        this.setter = setter;
        this.getter = getter;
    }

    /**
     * Returns the getter responsible for producing a {@code Collection} that the unmatched
     * arguments can be added to.
     */
    public IGetter getter() {
        return getter;
    }

    /** Returns the setter responsible for consuming the unmatched arguments. */
    public ISetter setter() {
        return setter;
    }

    void addAll(String[] unmatched) {
        if (setter != null) {
            try {
                setter.set(unmatched);
            } catch (Exception ex) {
                throw new PicocliException(String.format(
                        "Could not invoke setter (%s) with unmatched argument array '%s': %s",
                        setter, Arrays.toString(unmatched), ex), ex);
            }
        }
        if (getter != null) {
            try {
                Collection<String> collection = getter.get();
                Assert.notNull(collection, "getter returned null Collection");
                collection.addAll(Arrays.asList(unmatched));
            } catch (Exception ex) {
                throw new PicocliException(String.format(
                        "Could not add unmatched argument array '%s' to collection returned by getter (%s): %s",
                        Arrays.toString(unmatched), getter, ex), ex);
            }
        }
    }
}
