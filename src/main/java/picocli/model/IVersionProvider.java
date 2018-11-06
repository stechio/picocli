package picocli.model;

import picocli.annots.Command;

/**
 * Provides version information for a command. Commands may configure a provider with the
 * {@link Command#versionProvider()} annotation attribute.
 * 
 * @since 2.2
 */
public interface IVersionProvider {
    /**
     * Returns version information for a command.
     * 
     * @return version information (each string in the array is displayed on a separate line)
     * @throws Exception
     *             an exception detailing what went wrong when obtaining version information
     */
    String[] getVersion() throws Exception;
}