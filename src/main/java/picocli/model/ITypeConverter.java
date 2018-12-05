package picocli.model;

import picocli.annot.Option;
import picocli.annot.Parameters;

/**
 * <p>
 * When parsing command line arguments and initializing fields annotated with {@link Option @Option}
 * or {@link Parameters @Parameters}, String values can be converted to any type for which a
 * {@code ITypeConverter} is registered.
 * </p>
 * <p>
 * This interface defines the contract for classes that know how to convert a String into some
 * domain object. Custom converters can be registered with the
 * {@link #registerConverter(Class, ITypeConverter)} method.
 * </p>
 * <p>
 * Java 8 lambdas make it easy to register custom type converters:
 * </p>
 * 
 * <pre>
 * commandLine.registerConverter(java.nio.file.Path.class, s -&gt; java.nio.file.Paths.get(s));
 * commandLine.registerConverter(java.time.Duration.class, s -&gt; java.time.Duration.parse(s));
 * </pre>
 * <p>
 * Built-in type converters are pre-registered for the following java 1.5 types:
 * </p>
 * <ul>
 * <li>all primitive types</li>
 * <li>all primitive wrapper types: Boolean, Byte, Character, Double, Float, Integer, Long,
 * Short</li>
 * <li>any enum</li>
 * <li>java.io.File</li>
 * <li>java.math.BigDecimal</li>
 * <li>java.math.BigInteger</li>
 * <li>java.net.InetAddress</li>
 * <li>java.net.URI</li>
 * <li>java.net.URL</li>
 * <li>java.nio.charset.Charset</li>
 * <li>java.sql.Time</li>
 * <li>java.util.Date</li>
 * <li>java.util.UUID</li>
 * <li>java.util.regex.Pattern</li>
 * <li>StringBuilder</li>
 * <li>CharSequence</li>
 * <li>String</li>
 * </ul>
 * 
 * @param <T>
 *            the type of the object that is the result of the conversion
 */
public interface ITypeConverter<T> {
    /**
     * Gets the description of the given value.
     * 
     * @param value
     *            Either model or view value.
     * @return
     */
    default String descriptionOf(Object value) {
        return null;
    }

    /**
     * Converts the specified command line argument value to its corresponding domain object.
     * 
     * @param value
     *            Command line argument value.
     */
    T modelOf(String value);

    /**
     * Converts the specified domain object to its corresponding command line argument value.
     * 
     * @param value
     *            Domain object.
     */
    String viewOf(Object value);
}