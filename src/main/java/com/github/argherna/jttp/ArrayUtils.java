package com.github.argherna.jttp;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Array utilities not in {@link Arrays}.
 */
class ArrayUtils {

    private ArrayUtils() {
    }

    /**
     * @param appendee    the array to be appended to
     * @param append      the array to be appended to the appendee
     * @param appendeeIdx offset index in appendee to start appending
     * @return value of appendeeIdx after appending.
     */
    static int appendTo(char[] appendee, char[] append, int appendeeIdx) {
        for (int i = 0; i < append.length; i++) {
            appendee[appendeeIdx++] = append[i];
        }
        return appendeeIdx;
    }

    /**
     * @param c array to have null characters removed from.
     * @return a new copy of c with no null characters.
     */
    static char[] trimNulls(char[] c) {
        var nullchars = 0;
        for (int i = c.length - 1; i >= 0; i--) {
            if (c[i] == '\0') {
                nullchars++;
            }
        }
        return Arrays.copyOf(c, c.length - nullchars);
    }

    /**
     * @param ignoreWhitespace if {@code true}, ignores leading whitespace in c.
     * @param c                the target array
     * @param cs               characters to test (in specified order) that the array starts
     *                         with.
     * @return {@code true} if c has the characters from its first index up to the number of
     *         test characters in argument order.
     * @throws IllegalArgumentException if the number of test characters exceeds the length of
     *                                  the target array.
     */
    static boolean startsWith(boolean ignoreWhitespace, char[] c, char... cs) {
        if (cs.length > c.length) {
            throw new IllegalArgumentException(Jttp.RB.getString("error.util.too.many.chars"));
        }
        var startsWith = true;
        for (int i = 0, j = 0; i < cs.length; i++, j++) {
            if (ignoreWhitespace) {
                while (Character.isWhitespace(c[j])) {
                    j++;
                    if (j == c.length) {
                        return false;
                    }
                }
            }
            startsWith = startsWith && (cs[i] == c[j]);
        }
        return startsWith;
    }

    /**
     * @param c  the target array
     * @param cs characters to test (in reverse of specified order) that the array ends with.
     * @return {@code true} if c has the characters from its last index down to the number of
     *         tet characters in reverse argument order.
     * @throws IllegalArgumentException if the number of test characters exceeds the length of
     *                                  the target array.
     */
    static boolean endsWith(char[] c, char... cs) {
        if (cs.length > c.length) {
            throw new IllegalArgumentException(Jttp.RB.getString("error.util.too.many.chars"));
        }
        var endsWith = true;
        for (int i = cs.length - 1, j = c.length - 1; i >= 0; i--, j--) {
            endsWith = endsWith && (cs[i] == c[j]);
        }
        return endsWith;
    }

    /**
     * @param c array to search.
     * @param t target character to find.
     * @return index in the array after the first appearance of the target character.
     */
    static int indexAfter(char[] c, char t) {
        var i = 0;
        while (c[i] != t && i != c.length) {
            i++;
        }
        return i == c.length ? -1 : i + 1;
    }

    /**
     * @param c characters to put into an array.
     * @return a char array whose length is the number of chars passed to it and whose contents
     *         are the chars passed to it in order.
     */
    static char[] asArray(char... c) {
        var buffer = new char[c.length];
        IntStream.range(0, buffer.length).forEach(i -> buffer[i] = c[i]);
        return buffer;
    }
}