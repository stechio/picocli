package picocli.annot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * Fields annotated with {@code @ParentCommand} will be initialized with the parent command of
 * the current subcommand. If the current command does not have a parent command, this
 * annotation has no effect.
 * </p>
 * <p>
 * Parent commands often define options that apply to all the subcommands. This annotation
 * offers a convenient way to inject a reference to the parent command into a subcommand, so the
 * subcommand can access its parent options. For example:
 * </p>
 * 
 * <pre>
 * &#064;Command(name = "top", subcommands = Sub.class)
 * class Top implements Runnable {
 *
 *     &#064;Option(names = { "-d",
 *             "--directory" }, description = "this option applies to all subcommands")
 *     File baseDirectory;
 *
 *     public void run() {
 *         System.out.println("Hello from top");
 *     }
 * }
 *
 * &#064;Command(name = "sub")
 * class Sub implements Runnable {
 *
 *     &#064;ParentCommand
 *     private Top parent;
 *
 *     public void run() {
 *         System.out.println("Subcommand: parent command 'directory' is " + parent.baseDirectory);
 *     }
 * }
 * </pre>
 * 
 * @since 2.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ParentCommand {
}