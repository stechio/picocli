package picocli.model;

import picocli.except.PicocliException;

/**
 * Customizable setter for modifying the value of an option or positional parameter. When an
 * option or positional parameter is matched on the command line, its setter is invoked to
 * capture the value. For example, an option can be bound to a field or a method, and when
 * the option is matched on the command line, the field's value is set or the method is
 * invoked with the option parameter value.
 * 
 * @since 3.0
 */
public interface ISetter {
    /**
     * Sets the new value of the option or positional parameter.
     *
     * @param value
     *            the new value of the option or positional parameter
     * @param <T>
     *            type of the value
     * @return the previous value of the binding (if supported by this binding)
     * @throws PicocliException
     *             if a problem occurred while setting the new value
     * @throws Exception
     *             internally, picocli call sites will catch any exceptions thrown from here
     *             and rethrow them wrapped in a PicocliException
     */
    <T> T set(T value) throws Exception;
}