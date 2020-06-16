package com.github.argherna.jttp;

/**
 * Base class for all renderers.
 */
abstract class Renderer {

    static final char[] EMPTY_BUFFER = new char[0];

    static final Integer FGCODE_LEN = 5;

    private final ColorTheme colorTheme;

    private final boolean colorOutput;

    private int currentPosition;

    private int mark;

    private AnsiColor currColor = AnsiColor.DEFAULT;

    private AnsiColor prevColor = AnsiColor.DEFAULT;

    /**
     * Constructs a Renderer with the default ColorTheme.
     */
    Renderer() {
        this(ColorTheme.DEFAULT);
    }

    /**
     * Constructs a new Renderer.
     * 
     * @param colorOutput  if {@code true} output in color.
     * @param indentOutput if {@code true} indent output.
     */
    Renderer(boolean colorOutput) {
        this(ColorTheme.DEFAULT, colorOutput);
    }

    /**
     * Constructs a Renderer.
     * 
     * @param colorTheme ColorTheme to use if color output is supported.
     */
    Renderer(ColorTheme colorTheme) {
        this(colorTheme, true);
    }

    /**
     * Constructs a Renderer.
     * 
     * @param colorTheme  ColorTheme to use if color output is supported.
     * @param colorOutput if {@code true} output in color.
     */
    Renderer(ColorTheme colorTheme, boolean colorOutput) {
        this.colorTheme = colorTheme;
        this.colorOutput = colorOutput;
    }

    /**
     * Set the AnsiColor to use.
     * 
     * @param color AnsiColor.
     */
    void setColor(AnsiColor color) {
        prevColor = currColor;
        currColor = color;
    }

    /**
     * Resets AnsiColor to signal no changes have been made.
     */
    void resetColor() {
        prevColor = currColor;
    }

    /**
     * @return {@code true} if {@link #setColor(AnsiColor) setColor} was called.
     */
    boolean colorChanged() {
        return prevColor != currColor;
    }

    /**
     * @return the AnsiColor.
     */
    AnsiColor getColor() {
        return currColor;
    }

    /**
     * @return char array of AnsiColor's {@value Jttp#AnsiColor#fgCode() fgCode} value.
     */
    char[] getColorFgCode() {
        return currColor.fgCode().toCharArray();
    }

    /**
     * Mark the current position in a buffer.
     */
    void mark() {
        mark = currentPosition;
    }

    /**
     * Set the current position to the mark.
     */
    void reset() {
        currentPosition = mark;
        mark = 0;
    }

    /**
     * @return the current position.
     */
    final int getCurrentPosition() {
        return currentPosition;
    }

    final void incrementCurrentPosition() {
        currentPosition++;
    }

    final void incrementCurrentPositionBy(int inc) {
        currentPosition = currentPosition + inc;
    }

    final int getAndIncrementCurrentPosition() {
        return currentPosition++;
    }

    final int incrementAndGetCurrentPosition() {
        return ++currentPosition;
    }

    final void decrementCurrentPosition() {
        currentPosition--;
    }

    final int decrementAndGetCurrentPosition() {
        decrementCurrentPosition();
        return getCurrentPosition();
    }

    final void zeroCurrentPosition() {
        currentPosition = 0;
    }

    final ColorTheme getColorTheme() {
        return colorTheme;
    }

    final boolean isColorOutput() {
        return colorOutput;
    }
}