package picocli.model;

import picocli.annot.Command;

/**
 * Provides default value for a command. Commands may configure a provider with the
 * {@link Command#defaultValueProvider()} annotation attribute.
 * 
 * @since 3.6
 */
public interface IDefaultValueProvider {

    /**
     * Returns the default value for an option or positional parameter or {@code null}. The
     * returned value is converted to the type of the option/positional parameter via the same
     * type converter used when populating this option/positional parameter from a command line
     * argument.
     * 
     * @param argSpec
     *            the option or positional parameter, never {@code null}
     * @return the default value for the option or positional parameter, or {@code null} if this
     *         provider has no default value for the specified option or positional parameter
     * @throws Exception
     *             when there was a problem obtaining the default value
     */
    String defaultValue(ArgSpec argSpec) throws Exception;
}