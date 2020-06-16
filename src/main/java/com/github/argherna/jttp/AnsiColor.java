package com.github.argherna.jttp;

import static java.lang.String.format;
import static java.util.Objects.isNull;

/**
 * ANSI Color enumeration.
 */
enum AnsiColor {

    BLACK(0), RED(1), GREEN(2), YELLOW(3), BLUE(4), MAGENTA(5), CYAN(6), WHITE(7), DEFAULT(9);

    private static final char FIRST_ESC_CHAR = '\u001B';
    private static final char SECOND_ESC_CHAR = '[';

    private final int code;

    private String fgCode;

    private AnsiColor(int code) {
        this.code = code;
    }

    /**
     * @return foreground code for this AnsiColor.
     */
    public String fgCode() {
        if (isNull(fgCode)) {
            fgCode = format("%c%c%dm", FIRST_ESC_CHAR, SECOND_ESC_CHAR, code + 30);
        }
        return fgCode;
    }
}