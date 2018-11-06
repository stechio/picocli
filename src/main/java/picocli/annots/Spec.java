package picocli.annots;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fields annotated with {@code @Spec} will be initialized with the {@code CommandSpec} for the
 * command the field is part of. Example usage:
 * 
 * <pre>
 * class InjectSpecExample implements Runnable {
 *     &#064;Spec
 *     CommandSpec commandSpec;
 * 
 *     //...
 *     public void run() {
 *         // do something with the injected objects
 *     }
 * }
 * </pre>
 * 
 * @since 3.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface Spec {
}