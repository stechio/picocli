package picocli.annot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import picocli.except.DuplicateOptionAnnotationsException;

/**
 * <p>
 * Fields annotated with {@code @Mixin} are "expanded" into the current command:
 * {@link Option @Option} and {@link Parameters @Parameters} in the mixin class are added to the
 * options and positional parameters of this command. A
 * {@link DuplicateOptionAnnotationsException} is thrown if any of the options in the mixin has
 * the same name as an option in this command.
 * </p>
 * <p>
 * The {@code Mixin} annotation provides a way to reuse common options and parameters without
 * subclassing. For example:
 * </p>
 * 
 * <pre>
 * class HelloWorld implements Runnable {
 *
 *     // adds the --help and --version options to this command
 *     &#064;Mixin
 *     private HelpOptions = new HelpOptions();
 *
 *     &#064;Option(names = {"-u", "--userName"}, required = true, description = "The user name")
 *     String userName;
 *
 *     public void run() { System.out.println("Hello, " + userName); }
 * }
 *
 * // Common reusable help options.
 * class HelpOptions {
 *
 *     &#064;Option(names = { "-h", "--help"}, usageHelp = true, description = "Display this help and exit")
 *     private boolean help;
 *
 *     &#064;Option(names = { "-V", "--version"}, versionHelp = true, description = "Display version info and exit")
 *     private boolean versionHelp;
 * }
 * </pre>
 * 
 * @since 3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
public @interface Mixin {
    /**
     * Optionally specify a name that the mixin object can be retrieved with from the
     * {@code CommandSpec}. If not specified the name of the annotated field is used.
     * 
     * @return a String to register the mixin object with, or an empty String if the name of the
     *         annotated field should be used
     */
    String name() default "";
}