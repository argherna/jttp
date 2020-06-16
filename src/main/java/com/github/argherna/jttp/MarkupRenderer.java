package com.github.argherna.jttp;

import java.io.CharArrayReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.function.Predicate;

import javax.xml.transform.stream.StreamSource;

/**
 * Renders HTML and XML.
 */
class MarkupRenderer extends ContentRenderer {

    private static final char[] END_COMMENT_ARY = ArrayUtils.asArray('-', '-', '>');

    private static final Predicate<Character> CHAR_NOT_CLOSE_TAG = c -> c != '>';

    private static final Predicate<Character> CHAR_CLOSE_TAG = Predicate.not(CHAR_NOT_CLOSE_TAG);

    private static final Predicate<Character> CHAR_NOT_OPEN_TAG = c -> c != '<';

    private static final Predicate<Character> CHAR_CHANGE_COLOR_IN_TAG =
            c -> c == '=' || c == '"' || c == ' ' || c == '?' || c == '/' || c == '\'';

    private final char[] markup;

    private final PrintStream ps;

    private boolean inComment = false;

    private boolean inDeclarativeStatement = false;

    /**
     * Constructs a new MarkupRenderer.
     * 
     * @param markup       the characters to render,
     * @param ps           the PrintStream.
     * @param colorOutput  if {@code true} output in color.
     * @param indentOutput if {@code true} indent output.
     */
    MarkupRenderer(char[] markup, PrintStream ps, boolean colorOutput, boolean indentOutput) {
        super(colorOutput, indentOutput);
        this.ps = ps;
        this.markup = isIndentOutput() ? indent(markup) : Arrays.copyOf(markup, markup.length);
    }

    /**
     * @param markup char array to indent.
     * @return char array of indented markup.
     */
    private char[] indent(char[] markup) {
        try {
            return Util.formatMarkup(new StreamSource(new CharArrayReader(markup))).toCharArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        var buffer = EMPTY_BUFFER;
        var ch = '\0';

        while (getCurrentPosition() < markup.length) {
            ch = markup[getCurrentPosition()];

            if (ch == '<') {
                var next = markup[incrementAndGetCurrentPosition()];
                if (next == '?' || next == '/' || Character.isLetterOrDigit(next)) {
                    setColor(getColorTheme().getKeywordValueColor());
                } else if (next == '!') {
                    setColor(getColorTheme().getNumericValueColor());
                    if (markup[getCurrentPosition() + 1] == '-'
                            && markup[getCurrentPosition() + 2] == '-') {
                        inComment = true;
                    } else {
                        inDeclarativeStatement = true;
                    }
                }
                int bufSz = inComment ? scanForCommentBufSz() : scanForTagBufSz();
                buffer = inComment
                        ? fillCommentBuffer(bufSz, ch, next,
                                markup[incrementAndGetCurrentPosition()],
                                markup[incrementAndGetCurrentPosition()])
                        : fillTagBuffer(bufSz, ch, next);
            } else if (Character.isWhitespace(ch)) {
                int bufSz = scanForWhitespaceBufSz();
                buffer = fillWhitespaceBuffer(bufSz, ch);
            } else {
                setColor(getColorTheme().getDefaultColor());
                int bufSz = scanForTextBufSz();
                buffer = fillTextBuffer(bufSz, ch);
            }

            if (isColorOutput() && colorChanged()) {
                ps.print(getColorFgCode());
                resetColor();
            }

            if (buffer.length > 0) {
                ps.print(buffer);
                buffer = EMPTY_BUFFER;
            } else if (!isIndentOutput()) {
                ps.print(ch);
            }

            inDeclarativeStatement = false;
            incrementCurrentPosition();
        }
    }

    private int scanForTagBufSz() {
        var bufSz = 2;
        mark();
        var ch = markup[incrementAndGetCurrentPosition()];
        var colorChanges = 0;
        var instring = false;
        var lastIndexOfQuote = -1;
        while (CHAR_NOT_CLOSE_TAG.test(ch)) {
            bufSz++;
            if (CHAR_CHANGE_COLOR_IN_TAG.test(ch) && !inDeclarativeStatement && !instring) {
                colorChanges++;
            }
            if (ch == '"' || ch == '\'') {
                instring = !instring;
                if (!instring) {
                    colorChanges++;
                }
                lastIndexOfQuote = getCurrentPosition();
            }
            ch = markup[incrementAndGetCurrentPosition()];
        }
        // Account for the closing character and possible color changes if for example there's
        // an unquoted attribute value.
        bufSz++;
        if (lastIndexOfQuote == -1) {
            colorChanges++;
        }
        // Account for anything that could cause the color to change inside a tag if needed.
        if (isColorOutput()) {
            bufSz = bufSz + (FGCODE_LEN * colorChanges);
        }
        reset();
        return bufSz;
    }

    private int scanForCommentBufSz() {
        var bufSz = 4; // start with <!--
        mark();
        incrementCurrentPositionBy(2);
        var ch = markup[incrementAndGetCurrentPosition()];
        var last3chars = ArrayUtils.asArray(markup[getCurrentPosition() - 2],
                markup[getCurrentPosition() - 1], ch);
        while (!Arrays.equals(END_COMMENT_ARY, last3chars)) {
            bufSz++;
            ch = markup[incrementAndGetCurrentPosition()];
            last3chars = ArrayUtils.asArray(markup[getCurrentPosition() - 2],
                    markup[getCurrentPosition() - 1], ch);
        }
        bufSz++; // Get the close tag character.
        reset();
        return bufSz;
    }

    private int scanForWhitespaceBufSz() {
        var bufSz = 1;
        if (getCurrentPosition() < markup.length - 1) {
            mark();
            var ch = markup[incrementAndGetCurrentPosition()];
            while (Character.isWhitespace(ch)) {
                bufSz++;
                if (getCurrentPosition() == markup.length - 1) {
                    break;
                }
                ch = markup[incrementAndGetCurrentPosition()];
            }
            reset();
        }
        return bufSz;
    }

    private int scanForTextBufSz() {
        var bufSz = 1;
        if (getCurrentPosition() < markup.length - 1) {
            mark();
            var ch = markup[incrementAndGetCurrentPosition()];
            while (CHAR_NOT_OPEN_TAG.test(ch) && getCurrentPosition() < markup.length) {
                bufSz++;
                ch = markup[incrementAndGetCurrentPosition()];
            }
            reset();
        }
        return bufSz;
    }

    private char[] fillTagBuffer(int bufSz, char first, char second) {
        var buffer = new char[bufSz];
        var bufIdx = 0;
        buffer[bufIdx++] = first;
        buffer[bufIdx++] = second;
        var ch = markup[incrementAndGetCurrentPosition()];
        var currColorChars = getColorFgCode();
        while (CHAR_NOT_CLOSE_TAG.test(ch) && bufIdx < bufSz
                && getCurrentPosition() < markup.length) {
            if (!inDeclarativeStatement && CHAR_CHANGE_COLOR_IN_TAG.test(ch)
                    && CHAR_NOT_CLOSE_TAG.test(markup[getCurrentPosition() + 1])) {
                if (ch == ' ' && markup[getCurrentPosition() - 1] != ' ') {
                    buffer[bufIdx++] = ch;
                    if (isColorOutput()) {
                        currColorChars = getColorTheme().getKeyColor().fgCode().toCharArray();
                        bufIdx = ArrayUtils.appendTo(buffer, currColorChars, bufIdx);
                    }
                    ch = markup[incrementAndGetCurrentPosition()];
                    while (!CHAR_CHANGE_COLOR_IN_TAG.test(ch) && CHAR_NOT_CLOSE_TAG.test(ch)
                            && bufIdx < bufSz && getCurrentPosition() < markup.length - 1) {
                        buffer[bufIdx++] = ch;
                        ch = markup[incrementAndGetCurrentPosition()];
                    }
                } else if (ch == '=') {
                    if (isColorOutput()) {
                        currColorChars =
                                getColorTheme().getPunctuationColor().fgCode().toCharArray();
                        bufIdx = ArrayUtils.appendTo(buffer, currColorChars, bufIdx);
                    }
                    buffer[bufIdx++] = ch;
                    ch = markup[incrementAndGetCurrentPosition()];
                } else if (ch == '"' || ch == '\'') {
                    var strDelimiter = ch;
                    if (isColorOutput()) {
                        currColorChars =
                                getColorTheme().getStringValueColor().fgCode().toCharArray();
                        bufIdx = ArrayUtils.appendTo(buffer, currColorChars, bufIdx);
                    }

                    do {
                        buffer[bufIdx++] = ch;
                        ch = markup[incrementAndGetCurrentPosition()];
                    } while (ch != strDelimiter && bufIdx < bufSz);

                    if (ch == strDelimiter) {
                        buffer[bufIdx++] = ch;
                    }
                    ch = markup[incrementAndGetCurrentPosition()];
                } else if (ch == '?' || ch == '/') {
                    if (isColorOutput()) {
                        // currColorChars = keywordColorChars;
                        currColorChars =
                                getColorTheme().getKeywordValueColor().fgCode().toCharArray();
                        bufIdx = ArrayUtils.appendTo(buffer, currColorChars, bufIdx);
                    }
                    buffer[bufIdx++] = ch;
                    ch = markup[incrementAndGetCurrentPosition()];
                } else {
                    buffer[bufIdx++] = ch;
                    ch = markup[incrementAndGetCurrentPosition()];
                }
            } else {
                buffer[bufIdx++] = ch;
                ch = markup[incrementAndGetCurrentPosition()];
            }
        }
        if (CHAR_CLOSE_TAG.test(ch) && !inDeclarativeStatement && !Arrays.equals(currColorChars,
                getColorTheme().getKeywordValueColor().fgCode().toCharArray())) {
            bufIdx = ArrayUtils.appendTo(buffer,
                    getColorTheme().getKeywordValueColor().fgCode().toCharArray(), bufIdx);
        }
        buffer[bufIdx] = ch;
        return ArrayUtils.trimNulls(buffer);
    }

    private char[] fillCommentBuffer(int bufSz, char first, char second, char third, char fourth) {
        var buffer = new char[bufSz];
        var bufIdx = 0;
        buffer[bufIdx++] = first;
        buffer[bufIdx++] = second;
        buffer[bufIdx++] = third;
        buffer[bufIdx++] = fourth;
        var ch = markup[incrementAndGetCurrentPosition()];
        var last3chars = ArrayUtils.asArray(markup[getCurrentPosition() - 2],
                markup[getCurrentPosition() - 1], ch);
        while (!Arrays.equals(END_COMMENT_ARY, last3chars)) {
            buffer[bufIdx++] = ch;
            ch = markup[incrementAndGetCurrentPosition()];
            last3chars = ArrayUtils.asArray(markup[getCurrentPosition() - 2],
                    markup[getCurrentPosition() - 1], ch);
        }
        buffer[bufIdx] = ch;
        return buffer;
    }

    private char[] fillTextBuffer(int bufSz, char first) {
        var buffer = new char[bufSz];
        var bufIdx = 0;
        buffer[bufIdx++] = first;
        if (getCurrentPosition() < markup.length - 1) {
            var ch = markup[incrementAndGetCurrentPosition()];
            while (CHAR_NOT_OPEN_TAG.test(ch)) {
                buffer[bufIdx++] = ch;
                ch = markup[incrementAndGetCurrentPosition()];
            }
            // Back up 1 character becaue the open tag is at the current position.
            decrementCurrentPosition();
        }
        return ArrayUtils.trimNulls(buffer);
    }

    private char[] fillWhitespaceBuffer(int bufSz, char first) {
        var buffer = new char[bufSz];
        var bufIdx = 0;
        buffer[bufIdx++] = first;
        if (getCurrentPosition() < markup.length - 1) {
            var ch = markup[incrementAndGetCurrentPosition()];
            while (Character.isWhitespace(ch)) {
                buffer[bufIdx++] = ch;
                if (getCurrentPosition() == markup.length - 1) {
                    break;
                }
                ch = markup[incrementAndGetCurrentPosition()];
            }
            decrementCurrentPosition();
        }
        return ArrayUtils.trimNulls(buffer);
    }
}
