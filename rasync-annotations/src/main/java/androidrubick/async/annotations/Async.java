package androidrubick.async.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * indicate target Type (interface) or method should
 *
 * generate a async proxy
 *
 * @since 1.0.0
 */
@Retention(CLASS)
@Target({TYPE})
//@Target({TYPE, METHOD})
public @interface Async {
}
