package org.dataone.notifications.util;

/**
 * A utility class for string manipulation.
 * Created this because it did not make sense to add a dependency on Apache Commons Lang just for
 * the {@code isBlank(String)} method. Can refactor this and start using Apache Commons Lang, if we
 * end up needing more string manipulation utilities
 */
public class StringUtils {

        /**
        * Checks if a string is null or empty.
        *
        * @param str the string to check
        * @return {@code true} if the string is null or empty, {@code false} otherwise
        */
        public static boolean isBlank(String str) {
            return str == null || str.isBlank();
        }
}
