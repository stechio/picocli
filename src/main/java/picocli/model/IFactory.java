package picocli.model;

import picocli.CommandLine;
import picocli.annots.Command;
import picocli.annots.Option;
import picocli.annots.Parameters;

/**
 * Factory for instantiating classes that are registered declaratively with annotation
 * attributes, like {@link Command#subcommands()}, {@link Option#converter()},
 * {@link Parameters#converter()} and {@link Command#versionProvider()}.
 * <p>
 * The default factory implementation simply creates a new instance of the specified class when
 * {@link #create(Class)} is invoked.
 * </p>
 * <p>
 * You may provide a custom implementation of this interface. For example, a custom factory
 * implementation could delegate to a dependency injection container that provides the requested
 * instance.
 * </p>
 * 
 * @see picocli.CommandLine#CommandLine(Object, IFactory)
 * @see #call(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
 * @see #run(Class, IFactory, PrintStream, PrintStream, Help.Ansi, String...)
 * @since 2.2
 */
public interface IFactory {
    /**
     * Returns an instance of the specified class.
     * 
     * @param cls
     *            the class of the object to return
     * @param <K>
     *            the type of the object to return
     * @return the instance
     * @throws Exception
     *             an exception detailing what went wrong when creating or obtaining the
     *             instance
     */
    <K> K create(Class<K> cls) throws Exception;
}