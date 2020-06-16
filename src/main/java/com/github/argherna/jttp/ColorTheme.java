package com.github.argherna.jttp;

/**
 * ColorTheme for renderers.
 */
enum ColorTheme {

    DEFAULT(AnsiColor.WHITE, AnsiColor.CYAN, AnsiColor.BLUE, AnsiColor.MAGENTA,
            AnsiColor.YELLOW, AnsiColor.DEFAULT, AnsiColor.GREEN);

    private final AnsiColor punctuationColor;

    private final AnsiColor keyColor;

    private final AnsiColor keywordValueColor;

    private final AnsiColor numericValueColor;

    private final AnsiColor stringValueColor;

    private final AnsiColor defaultColor;

    private final AnsiColor functionColor;

    private ColorTheme(AnsiColor punctuationColor, AnsiColor keyColor,
            AnsiColor keywordValueColor, AnsiColor numericValueColor,
            AnsiColor stringValueColor, AnsiColor defaultColor, AnsiColor functionColor) {
        this.punctuationColor = punctuationColor;
        this.keyColor = keyColor;
        this.keywordValueColor = keywordValueColor;
        this.numericValueColor = numericValueColor;
        this.stringValueColor = stringValueColor;
        this.defaultColor = defaultColor;
        this.functionColor = functionColor;
    }

    public AnsiColor getPunctuationColor() {
        return punctuationColor;
    }

    public AnsiColor getKeyColor() {
        return keyColor;
    }

    public AnsiColor getKeywordValueColor() {
        return keywordValueColor;
    }

    public AnsiColor getNumericValueColor() {
        return numericValueColor;
    }

    public AnsiColor getStringValueColor() {
        return stringValueColor;
    }

    public AnsiColor getDefaultColor() {
        return defaultColor;
    }

    public AnsiColor getFunctionColor() {
        return functionColor;
    }
}