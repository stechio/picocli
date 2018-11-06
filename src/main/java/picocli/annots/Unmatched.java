package picocli.annots;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import picocli.CommandLine;

/**
 * Fields annotated with {@code @Unmatched} will be initialized with the list of unmatched
 * command line arguments, if any. If this annotation is found, picocli automatically sets
 * {@linkplain CommandLine#setUnmatchedArgumentsAllowed(boolean) unmatchedArgumentsAllowed} to
 * {@code true}.
 * 
 * @see CommandLine#isUnmatchedArgumentsAllowed()
 * @since 3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Unmatched {
}