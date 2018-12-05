package picocli.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import picocli.annot.Command;
import picocli.annot.Option;
import picocli.util.Assert;

/**
 * Utility class for getting resource bundle strings. Enhances the standard <a href=
 * "https://docs.oracle.com/javase/8/docs/api/java/util/ResourceBundle.html">ResourceBundle</a>
 * with support for String arrays and qualified keys: keys that may or may not be prefixed
 * with the fully qualified command name.
 * <p>
 * Example properties resource bundle:
 * </p>
 * 
 * <pre>
 * # Usage Help Message Sections
 * # ---------------------------
 * # Numbered resource keys can be used to create multi-line sections.
 * usage.headerHeading = This is my app. There are other apps like it but this one is mine.%n
 * usage.header   = header first line
 * usage.header.0 = header second line
 * usage.descriptionHeading = Description:%n
 * usage.description.0 = first line
 * usage.description.1 = second line
 * usage.description.2 = third line
 * usage.synopsisHeading = Usage:&#92;u0020
 * # Leading whitespace is removed by default. Start with &#92;u0020 to keep the leading whitespace.
 * usage.customSynopsis.0 =      Usage: ln [OPTION]... [-T] TARGET LINK_NAME   (1st form)
 * usage.customSynopsis.1 = &#92;u0020 or:  ln [OPTION]... TARGET                  (2nd form)
 * usage.customSynopsis.2 = &#92;u0020 or:  ln [OPTION]... TARGET... DIRECTORY     (3rd form)
 * # Headings can contain the %n character to create multi-line values.
 * usage.parameterListHeading = %nPositional parameters:%n
 * usage.optionListHeading = %nOptions:%n
 * usage.commandListHeading = %nCommands:%n
 * usage.footerHeading = Powered by picocli%n
 * usage.footer = footer
 *
 * # Option Descriptions
 * # -------------------
 * # Use numbered keys to create multi-line descriptions.
 * help = Show this help message and exit.
 * version = Print version information and exit.
 * </pre>
 * <p>
 * Resources for multiple commands can be specified in a single ResourceBundle. Keys and
 * their value can be shared by multiple commands (so you don't need to repeat them for
 * every command), but keys can be prefixed with {@code fully qualified command name + "."}
 * to specify different values for different commands. The most specific key wins. For
 * example:
 * </p>
 * 
 * <pre>
 * jfrog.rt.usage.header = Artifactory commands
 * jfrog.rt.config.usage.header = Configure Artifactory details.
 * jfrog.rt.upload.usage.header = Upload files.
 *
 * jfrog.bt.usage.header = Bintray commands
 * jfrog.bt.config.usage.header = Configure Bintray details.
 * jfrog.bt.upload.usage.header = Upload files.
 *
 * # shared between all commands
 * usage.footerHeading = Environment Variables:
 * usage.footer.0 = footer line 0
 * usage.footer.1 = footer line 1
 * </pre>
 * 
 * @see Command#resourceBundle()
 * @see Option#descriptionKey()
 * @see OptionSpec#description()
 * @see PositionalParamSpec#description()
 * @see CommandSpec#qualifiedName(String)
 * @since 3.6
 */
public class Messages {
    private final CommandSpec spec;
    private final ResourceBundle rb;
    private final Set<String> keys;

    public Messages(CommandSpec spec, ResourceBundle rb) {
        this.spec = Assert.notNull(spec, "CommandSpec");
        this.rb = rb;
        this.keys = keys(rb);
    }

    private static Set<String> keys(ResourceBundle rb) {
        if (rb == null) {
            return Collections.emptySet();
        }
        Set<String> keys = new LinkedHashSet<String>();
        for (Enumeration<String> k = rb.getKeys(); k.hasMoreElements(); keys
                .add(k.nextElement()))
            ;
        return keys;
    }

    /**
     * Returns a copy of the specified Messages object with the CommandSpec replaced by the
     * specified one.
     * 
     * @param spec
     *            the CommandSpec of the returned Messages
     * @param original
     *            the Messages object whose ResourceBundle to reference
     * @return a Messages object with the specified CommandSpec and the ResourceBundle of
     *         the specified Messages object
     */
    public static Messages copy(CommandSpec spec, Messages original) {
        return original == null ? null : new Messages(spec, original.rb);
    }

    /**
     * Returns {@code true} if the specified {@code Messages} is {@code null} or has a
     * {@code null ResourceBundle}.
     */
    public static boolean empty(Messages messages) {
        return messages == null || messages.rb == null;
    }

    /**
     * Returns the String value found in the resource bundle for the specified key, or the
     * specified default value if not found.
     * 
     * @param key
     *            unqualified resource bundle key. This method will first try to find a
     *            value by qualifying the key with the command's fully qualified name, and
     *            if not found, it will try with the unqualified key.
     * @param defaultValue
     *            value to return if the resource bundle is null or empty, or if no value
     *            was found by the qualified or unqualified key
     * @return the String value found in the resource bundle for the specified key, or the
     *         specified default value
     */
    public String getString(String key, String defaultValue) {
        if (rb == null || keys.isEmpty()) {
            return defaultValue;
        }
        String cmd = spec.qualifiedName(".");
        if (keys.contains(cmd + "." + key)) {
            return rb.getString(cmd + "." + key);
        }
        if (keys.contains(key)) {
            return rb.getString(key);
        }
        return defaultValue;
    }

    /**
     * Returns the String array value found in the resource bundle for the specified key, or
     * the specified default value if not found. Multi-line strings can be specified in the
     * resource bundle with {@code key.0}, {@code key.1}, {@code key.2}, etc.
     * 
     * @param key
     *            unqualified resource bundle key. This method will first try to find a
     *            value by qualifying the key with the command's fully qualified name, and
     *            if not found, it will try with the unqualified key.
     * @param defaultValues
     *            value to return if the resource bundle is null or empty, or if no value
     *            was found by the qualified or unqualified key
     * @return the String array value found in the resource bundle for the specified key, or
     *         the specified default value
     */
    public String[] getStringArray(String key, String[] defaultValues) {
        if (rb == null || keys.isEmpty()) {
            return defaultValues;
        }
        String cmd = spec.qualifiedName(".");
        List<String> result = addAllWithPrefix(rb, cmd + "." + key, keys,
                new ArrayList<String>());
        if (!result.isEmpty()) {
            return result.toArray(new String[0]);
        }
        addAllWithPrefix(rb, key, keys, result);
        return result.isEmpty() ? defaultValues : result.toArray(new String[0]);
    }

    private static List<String> addAllWithPrefix(ResourceBundle rb, String key,
            Set<String> keys, List<String> result) {
        if (keys.contains(key)) {
            result.add(rb.getString(key));
        }
        for (int i = 0; true; i++) {
            String elementKey = key + "." + i;
            if (keys.contains(elementKey)) {
                result.add(rb.getString(elementKey));
            } else {
                return result;
            }
        }
    }

    /**
     * Returns the ResourceBundle of the specified Messages object or {@code null} if the
     * specified Messages object is {@code null}.
     */
    public static ResourceBundle resourceBundle(Messages messages) {
        return messages == null ? null : messages.resourceBundle();
    }

    /** Returns the ResourceBundle of this object or {@code null}. */
    public ResourceBundle resourceBundle() {
        return rb;
    }

    /** Returns the CommandSpec of this object, never {@code null}. */
    public CommandSpec commandSpec() {
        return spec;
    }
}