package com.github.argherna.jttp;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Renders Json.
 */
class JsonRenderer extends ContentRenderer {

    private static final BiPredicate<Character, Character> IN_STRING =
            (c0, c1) -> (c0 == '\\' && c1 == '"') || c1 != '"';

    private static final Predicate<Character> CHAR_IS_DIGIT = c -> Character.isDigit(c);

    private static final Predicate<Character> CHAR_IS_LETTER = c -> Character.isLetter(c);

    private static final BiPredicate<Character, Character> IS_EMPTY_STRING =
            (c0, c1) -> c0 == '\"' && c1 == '\"';

    private static final Predicate<Character> IS_ESCAPE_CHAR =
            c -> c == '\b' || c == '\f' || c == '\n' || c == '\r';

    private final char[] json;

    private final PrintStream ps;

    /**
     * Constructs a new JsonRenderer.
     * 
     * @param json         characters to render.
     * @param ps           the PrintStream.
     * @param colorOutput  if {@code true} output in color.
     * @param indentOutput if {@code true} indent output.
     */
    JsonRenderer(char[] json, PrintStream ps, boolean colorOutput, boolean indentOutput) {
        super(colorOutput, indentOutput);
        this.json = Arrays.copyOf(json, json.length);
        this.ps = ps;
    }

    @Override
    public void run() {

        var buffer = EMPTY_BUFFER;
        var ch = '\0';
        zeroCurrentPosition();

        while (getCurrentPosition() < json.length) {
            ch = json[getCurrentPosition()];

            if (isPunctuation(ch)) {
                setColor(getColorTheme().getPunctuationColor());
                if (isOpenContainer(ch)) {
                    var next = json[getCurrentPosition() + 1];
                    if (isCloseContainer(next)) {
                        buffer = ArrayUtils.asArray(ch, next);
                        incrementCurrentPosition();
                    } else {
                        buffer = isIndentOutput()
                                ? toIndentedCharArray(ch, incrementAndGetIndentLevel())
                                : ArrayUtils.asArray(ch);
                    }
                } else if (ch == ',') {
                    buffer = isIndentOutput() ? toIndentedCharArray(ch, getIndentLevel())
                            : ArrayUtils.asArray(ch);
                } else if (ch == ':') {
                    if (isIndentOutput()) {
                        buffer = new char[2];
                        buffer[0] = ch;
                        buffer[1] = ' ';
                    } else {
                        buffer = ArrayUtils.asArray(ch);
                    }
                } else if (isCloseContainer(ch)) {
                    buffer = isIndentOutput() ? toUnindentedCharArray(ch) : ArrayUtils.asArray(ch);
                }
            } else if (ch == '"') {
                if (buffer == EMPTY_BUFFER) {
                    var bufSz = scanAheadForStringValueSize();
                    buffer = fillStringBuffer(bufSz, ch);
                    // Put the closing quote in the array.
                    if (bufSz > 2) {
                        buffer[buffer.length - 1] = ch;
                    }

                    mark();
                    // Look ahead to the next punctuation character
                    ch = getNextCharAfterWhitespace();
                    if (isPunctuation(ch)) {
                        if (ch == ':') {
                            setColor(getColorTheme().getKeyColor());
                        } else if (ch == ',' || isCloseContainer(ch)) {
                            setColor(getColorTheme().getStringValueColor());
                        }
                    }
                    // Reset the current position to resume processing.
                    reset();
                }
            } else if (Character.isDigit(ch)) {
                if (buffer == EMPTY_BUFFER) {
                    var bufSz = scanAheadForBufSz(CHAR_IS_DIGIT) - 1;
                    buffer = fillBuffer(CHAR_IS_DIGIT, bufSz, ch);

                    // Move the pointer back and mark our position since we will miss the ',' on
                    // the next iteration.
                    decrementCurrentPosition();
                    mark();

                    if (!isPunctuation(ch)) {
                        // Look ahead to the next punctuation character
                        ch = getNextCharAfterWhitespace();
                    }
                    if (isPunctuation(ch)) {
                        // Assume this is a bare number since the rules of json suggest so which
                        // means
                        // this punctuation character is a ','.
                        setColor(getColorTheme().getNumericValueColor());
                    }
                    // Reset the current position to resume processing.
                    reset();
                }
            } else if (ch == 't' || ch == 'f' || ch == 'n') {
                if (buffer == EMPTY_BUFFER) {
                    var bufSz = scanAheadForBufSz(CHAR_IS_LETTER) - 1;
                    buffer = fillBuffer(CHAR_IS_LETTER, bufSz, ch);

                    // Move the pointer back and mark our position since we will miss the ',' on
                    // the next iteration.
                    decrementCurrentPosition();
                    mark();

                    if (!isPunctuation(ch)) {
                        // Look ahead to the next punctuation character
                        ch = getNextCharAfterWhitespace();
                    }
                    if (isPunctuation(ch)) {
                        // Assume this is a bare number since the rules of json suggest so which
                        // means this punctuation character is a ','.
                        setColor(getColorTheme().getKeywordValueColor());
                    }
                    // Reset the current position to resume processing.
                    reset();
                }
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
            incrementCurrentPosition();
        }
    }

    private boolean isPunctuation(char ch) {
        return ch == '{' || ch == '}' || ch == ':' || ch == ',' || ch == '[' || ch == ']';
    }

    private boolean isOpenContainer(char ch) {
        return ch == '{' || ch == '[';
    }

    private boolean isCloseContainer(char ch) {
        return ch == ']' || ch == '}';
    }

    private char[] toIndentedCharArray(char c, int indentLevel) {
        var buffer = new char[(indentLevel * INDENT) + 2];
        var bufferIndex = 0;
        buffer[bufferIndex++] = c;
        buffer[bufferIndex++] = '\n';
        Arrays.fill(buffer, bufferIndex, buffer.length, ' ');
        return buffer;
    }

    private char[] toUnindentedCharArray(char c) {
        decrementIndentLevel();
        var buffer =
                (getIndentLevel() > -1) ? new char[(getIndentLevel() * INDENT) + 2] : new char[2];
        buffer[0] = '\n';
        if (getIndentLevel() > 0) {
            Arrays.fill(buffer, 1, buffer.length, ' ');
        }
        buffer[buffer.length - 1] = c;
        return buffer;
    }

    /**
     * @param scanCondition Condition that tests for end of scan.
     * @return the number of indexes in the json array scanned + 1 to account for quote strings.
     */
    private int scanAheadForBufSz(Predicate<Character> scanCondition) {
        // Already came across the first character.
        var bufSz = 1;
        mark();
        var ch = json[incrementAndGetCurrentPosition()];
        while (scanCondition.test(ch)) {
            bufSz++;
            ch = json[incrementAndGetCurrentPosition()];
        }
        // Account for the last character. This is the +1 and is needed for quoted text.
        bufSz++;
        reset();
        return bufSz;
    }

    private int scanAheadForStringValueSize() {
        var bufSz = 1;
        mark();
        var prev = json[getCurrentPosition()];
        var ch = json[incrementAndGetCurrentPosition()];
        var escapes = 0;
        do {
            if (IS_ESCAPE_CHAR.test(ch)) {
                escapes++;
            }
            bufSz++;
            if (IS_EMPTY_STRING.test(prev, ch)) {
                break;
            }
            prev = ch;
            ch = json[incrementAndGetCurrentPosition()];
        } while (IN_STRING.test(prev, ch));
        if (IS_EMPTY_STRING.negate().test(prev, ch)) {
            bufSz++;
        }
        reset();
        return bufSz + (escapes * 2);
    }

    private char[] fillBuffer(Predicate<Character> fillCondition, int bufSz, char c) {
        var buffer = new char[bufSz];
        var bufferIndex = 0;
        buffer[bufferIndex++] = c;
        var ch = json[incrementAndGetCurrentPosition()];
        while (fillCondition.test(ch)) {
            buffer[bufferIndex++] = ch;
            ch = json[incrementAndGetCurrentPosition()];
        }
        return buffer;
    }

    private char[] fillStringBuffer(int bufSz, char c) {
        var buffer = new char[bufSz];
        var bufferIndex = 0;
        buffer[bufferIndex++] = c;
        var prev = c;
        var ch = json[incrementAndGetCurrentPosition()];
        do {
            if (IS_ESCAPE_CHAR.test(ch)) {
                var escaped = toEscapeCharArray(ch);
                bufferIndex = ArrayUtils.appendTo(buffer, escaped, bufferIndex);
            } else {
                buffer[bufferIndex++] = ch;
            }
            if (IS_EMPTY_STRING.test(prev, ch)) {
                break;
            }
            prev = ch;
            ch = json[incrementAndGetCurrentPosition()];
        } while (IN_STRING.test(prev, ch));
        return buffer;
    }

    /**
     * Escape '\' and non-printable characters.
     * 
     * @param c char to escape.
     * @return the escaped printable characters as an array.
     */
    private char[] toEscapeCharArray(char c) {
        if (c == '\b') {
            return ArrayUtils.asArray('\\', 'b');
        } else if (c == '\f') {
            return ArrayUtils.asArray('\\', 'f');
        } else if (c == '\n') {
            return ArrayUtils.asArray('\\', 'n');
        } else if (c == '\r') {
            return ArrayUtils.asArray('\\', 'r');
        } else if (c == '\t') {
            return ArrayUtils.asArray('\\', 't');
        } else {
            return ArrayUtils.asArray('\\', '\\');
        }
    }

    private char getNextCharAfterWhitespace() {
        char c = json[incrementAndGetCurrentPosition()];
        // Eat the whitespace in the meantime (like if json was "key" : "value" or
        // "value" ,\n "key")
        while (Character.isWhitespace(c)) {
            c = json[incrementAndGetCurrentPosition()];
        }
        return c;
    }
}
