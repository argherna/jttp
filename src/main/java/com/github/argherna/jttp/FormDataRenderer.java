package com.github.argherna.jttp;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * Renders form data.
 */
class FormDataRenderer extends ContentRenderer {

    private final char[] formdata;

    private final PrintStream ps;

    /**
     * Constructs a new FormDataRenderer.
     * 
     * @param formdata    characters to render.
     * @param ps          the PrintStream.
     * @param colorOutput when {@code true}, print output in color.
     */
    FormDataRenderer(char[] formdata, PrintStream ps, boolean colorOutput) {
        super(colorOutput);
        this.formdata = Arrays.copyOf(formdata, formdata.length);
        this.ps = ps;
    }

    @Override
    public void run() {

        var buffer = EMPTY_BUFFER;
        var ch = '\0';
        zeroCurrentPosition();

        while (getCurrentPosition() < formdata.length) {
            ch = formdata[getCurrentPosition()];
            if (isConverted(ch)) {
                setColor(getColorTheme().getNumericValueColor());
                if (buffer == EMPTY_BUFFER) {
                    buffer = isStartHex(ch) ? fillHex(3, ch) : ArrayUtils.asArray(ch);
                }
            } else if (isSeparator(ch)) {
                setColor(getColorTheme().getKeyColor());
                buffer = ArrayUtils.asArray(ch);
            } else {
                setColor(getColorTheme().getDefaultColor());
                buffer = ArrayUtils.asArray(ch);
            }

            if (isColorOutput() && colorChanged()) {
                ps.print(getColorFgCode());
                resetColor();
            }

            ps.print(buffer);
            buffer = EMPTY_BUFFER;
            incrementCurrentPosition();
        }
    }

    private boolean isStartHex(char ch) {
        return ch == '%';
    }

    private boolean isConverted(char ch) {
        return isStartHex(ch) || ch == '+';
    }

    private boolean isSeparator(char ch) {
        return ch == '=' || ch == '&';
    }

    private char[] fillHex(int bufsz, char c) {
        var buffer = new char[bufsz];
        var bufferIdx = 0;
        buffer[bufferIdx++] = c;
        while (bufferIdx < bufsz) {
            var ch = formdata[incrementAndGetCurrentPosition()];
            buffer[bufferIdx++] = ch;
        }
        return buffer;
    }
}