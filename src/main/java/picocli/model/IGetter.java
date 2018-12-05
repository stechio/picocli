package picocli.model;

import picocli.except.PicocliException;

/**
 * Customizable getter for obtaining the current value of an option or positional parameter.
 * When an option or positional parameter is matched on the command line, its getter or
 * setter is invoked to capture the value. For example, an option can be bound to a field or
 * a method, and when the option is matched on the command line, the field's value is set or
 * the method is invoked with the option parameter value.
 * 
 * @since 3.0
 */
public interface IGetter {
    /**
     * Returns the current value of the binding. For multi-value options and positional
     * parameters, this method returns an array, collection or map to add values to.
     * 
     * @throws PicocliException
     *             if a problem occurred while obtaining the current value
     * @throws Exception
     *             internally, picocli call sites will catch any exceptions thrown from here
     *             and rethrow them wrapped in a PicocliException
     */
    <T> T get() throws Exception;
}