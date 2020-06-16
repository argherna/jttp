package com.github.argherna.jttp;

import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;

/**
 * Base class for rendering content.
 */
abstract class ContentRenderer extends Renderer implements Runnable {

    private static final Integer DEFAULT_INDENT = 2;

    static final Integer INDENT =
            AccessController.doPrivileged((PrivilegedAction<Integer>) () -> Integer
                    .getInteger(Jttp.SYS_PROP_INDENT, DEFAULT_INDENT));

    private final boolean indentOutput;

    private int indentLevel = 0;

    /**
     * Constructs a new ContentRenderer with indenting turned off.
     * 
     * @param colorOutput if {@code true} output in color.
     */
    ContentRenderer(boolean colorOutput) {
        this(colorOutput, false);
    }

    /**
     * Constructs a new ContentRenderer.
     * 
     * @param colorOutput  if {@code true} output in color.
     * @param indentOutput if {@code true} indent output.
     */
    ContentRenderer(boolean colorOutput, boolean indentOutput) {
        super(colorOutput);
        this.indentOutput = indentOutput;
    }

    /**
     * Returns a ContentRenderer that renders the characters directly to the PrintStream with no
     * coloring or indentation.
     * 
     * @param chars characters to render.
     * @param ps    the PrintStream.
     * @return a raw ContentRenderer.
     */
    static ContentRenderer newRawInstance(char[] chars, PrintStream ps) {
        return new RawContentRenderer(chars, ps);
    }

    final void zeroIndentLevel() {
        indentLevel = 0;
    }

    final boolean isIndentOutput() {
        return indentOutput;
    }

    final int getIndentLevel() {
        return indentLevel;
    }

    final void incrementIndentLevel() {
        indentLevel++;
    }

    final void decrementIndentLevel() {
        if (indentLevel - 1 >= 0) {
            indentLevel--;
        }
    }

    final int incrementAndGetIndentLevel() {
        incrementIndentLevel();
        return getIndentLevel();
    }

    final int decrementAndGetIndentLevel() {
        decrementIndentLevel();
        return getIndentLevel();
    }

    /**
     * ContentRenderer that prints the characters directly to a PrintStream with no formatting
     * or colors.
     */
    private static class RawContentRenderer extends ContentRenderer {

        private final char[] content;

        private final PrintStream ps;

        /**
         * Construct a new RawContentRenderer.
         * 
         * @param content characters to render.
         * @param ps      the PrintStream.
         */
        private RawContentRenderer(char[] content, PrintStream ps) {
            super(false, false);
            this.content = Arrays.copyOf(content, content.length);
            this.ps = ps;
        }

        @Override
        public void run() {
            ps.print(content);
        }
    }
}