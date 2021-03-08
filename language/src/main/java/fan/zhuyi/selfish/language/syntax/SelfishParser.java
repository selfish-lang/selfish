package fan.zhuyi.selfish.language.syntax;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import fan.zhuyi.selfish.language.node.*;

import java.awt.event.KeyEvent;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Callable;

public class SelfishParser {
    private final Source source;
    private final CharSequence data;
    private int offset;
    private boolean globalState;
    private final SelfishParserTable table = new SelfishParserTable();

    public final class SelfishSyntaxError extends Exception {
        private final int errOffset;
        private final boolean unclosedError;

        public SelfishSyntaxError(String message) {
            super(message);
            this.errOffset = offset;
            this.unclosedError = false;
        }

        public SelfishSyntaxError(String message, boolean unclosedError) {
            super(message);
            this.errOffset = offset;
            this.unclosedError = unclosedError;
        }

        public boolean isUnclosedError() {
            return unclosedError;
        }

        public int getErrOffset() {
            return errOffset;
        }


        @Override
        public String getMessage() {
            return source.getName() + ":"
                   + source.getLineNumber(this.errOffset)
                   + ":" + source.getColumnNumber(this.errOffset) + ": " + super.getMessage();
        }
    }

    public final class InternalParserError extends Exception {
        private final int errOffset;

        public InternalParserError(String message) {
            super(message);
            this.errOffset = offset;
        }

        @Override
        public String getMessage() {
            return source.getName() + ":"
                   + source.getLineNumber(this.errOffset)
                   + ":" + source.getColumnNumber(this.errOffset) + ": " + super.getMessage();
        }
    }

    public SelfishParser(Source source) {
        this.source = source;
        offset = 0;
        globalState = true;
        data = source.getCharacters();
    }

    public boolean isSuccess() {
        return globalState;
    }

    private char currentChar() throws IndexOutOfBoundsException {
        return data.charAt(offset);
    }


    private final static class CodePointCache {
        int codepoint = -1;
        int offset = -1;
    }

    private final CodePointCache cache = new CodePointCache();

    private void fillCodePoint() throws IndexOutOfBoundsException {
        cache.offset = offset;
        char first = data.charAt(offset);
        if (Character.isHighSurrogate(first)) {
            try {
                char second = data.charAt(offset + 1);
                if (Character.isLowSurrogate(second)) {
                    cache.codepoint = Character.toCodePoint(first, second);
                }
            } catch (IndexOutOfBoundsException ignored) {
                cache.codepoint = first;
            }
        } else {
            cache.codepoint = first;
        }
    }

    private int currentCodepoint() throws IndexOutOfBoundsException {
        if (cache.offset != offset) {
            fillCodePoint();
        }
        return cache.codepoint;
    }

    private void moveNextChar() {
        offset += 1;
    }

    private void moveNextCodePoint() {
        var length = Character.charCount(currentCodepoint());
        offset += length;
    }

    private void moveNextLine() {
        var line = source.getLineNumber(offset);
        var lineStart = source.getLineStartOffset(line);
        var lineLength = source.getLineLength(line);
        offset = lineStart + lineLength;
    }

    private void eatWhitespace() {
        try {
            while (Character.isWhitespace(currentChar()) || currentChar() == '#') {
                if (currentChar() == '#') {
                    moveNextLine();
                } else {
                    moveNextChar();
                }
            }
        } catch (IndexOutOfBoundsException ignored) {
        }
    }

    private static boolean isPrintableChar(int codepoint) {
        var block = Character.UnicodeBlock.of(codepoint);
        return (!Character.isISOControl(codepoint)) &&
               codepoint != KeyEvent.CHAR_UNDEFINED &&
               block != null &&
               (block != Character.UnicodeBlock.SPECIALS);
    }

    private static final int TILDE = 1 << 31;
    private static final int WILDCARD = 1 << 30;
    private static final int INVALID = 1 << 29;
    private static final int ENDING_HINT = 1 << 28;
    private static final CharsetEncoder ASCII = StandardCharsets.US_ASCII.newEncoder();

    private static int checkCodePoint(int codepoint) {
        switch (codepoint) {
            case '~':
                return TILDE;
            case '*':
                return WILDCARD;
            case '!':
            case '%':
            case '+':
            case '-':
            case ',':
            case '/':
            case '\\':
            case '_':
                return codepoint;
            case '|':
            case ';':
            case ')':
                return ENDING_HINT;
            default:
                if (Character.isWhitespace(codepoint)) return ENDING_HINT;
                if (Character.isDigit(codepoint)
                    || Character.isAlphabetic(codepoint)
                    || isPrintableChar(codepoint)) {
                    return codepoint;
                }
                if (Character.isBmpCodePoint(codepoint) && ASCII.canEncode((char) codepoint)) {
                    return INVALID;
                }
                return codepoint;

        }
    }


    private static final int ESCAPE_NONE = 0;
    private static final int ESCAPE_START = 1;
    private static final int ESCAPE_OCTAL = 2;
    private static final int ESCAPE_HEX = 3;
    private static final int ESCAPE_SMALL = 4;
    private static final int ESCAPE_LARGE = 5;
    private static final long ESCAPE_MODE_MASK = 0xFF0000000000L;
    private static final long ESCAPE_MODE_SHIFT = 40;
    private static final long ESCAPE_COUNT_MASK = 0xFF00000000L;
    private static final long ESCAPE_COUNT_SHIFT = 32;
    private static final long ESCAPE_ACC_MASK = 0xFFFFFFFFL;

    private static int getEscapeMode(long masked) {
        return (int) ((masked & ESCAPE_MODE_MASK) >> ESCAPE_MODE_SHIFT);
    }

    private static long getEscapeCount(long masked) {
        return (masked & ESCAPE_COUNT_MASK) >> ESCAPE_COUNT_SHIFT;
    }

    private static long resetEscapeMode(long mode) {
        return mode << ESCAPE_MODE_SHIFT;
    }

    private long testHex(long value) throws SelfishSyntaxError {
        if (value - '0' >= 0 && value - '0' <= 9) return value - '0';
        if (value - 'a' >= 0 && value - 'a' <= 5) return value - 'a' + 10L;
        if (value - 'A' >= 0 && value - 'A' <= 5) return value - 'A' + 10L;
        throw new SelfishSyntaxError("invalid hexadecimal digit");
    }

    private long addEscapeHex(long current, int hex) throws SelfishSyntaxError {
        assert getEscapeMode(current) == ESCAPE_HEX
               || getEscapeMode(current) == ESCAPE_SMALL
               || getEscapeMode(current) == ESCAPE_LARGE;
        final var msb = (current & (ESCAPE_MODE_MASK + ESCAPE_COUNT_MASK)) + (1L << ESCAPE_COUNT_SHIFT);
        final var lsb = getEscapeAccumulator(current);
        return msb | (lsb * 16L + testHex(hex));
    }

    private long addEscapeOct(long current, int oct) throws SelfishSyntaxError {
        assert getEscapeMode(current) == ESCAPE_OCTAL;
        if (oct < '0' || oct > '7') {
            throw new SelfishSyntaxError("invalid octal digit");
        }
        final var msb = (current & (ESCAPE_MODE_MASK + ESCAPE_COUNT_MASK)) + (1L << ESCAPE_COUNT_SHIFT);
        final var lsb = getEscapeAccumulator(current);
        return msb | (lsb * 8L + oct);
    }

    private static int getEscapeAccumulator(long masked) {
        return (int) (masked & ESCAPE_ACC_MASK);
    }


    private final class StringState {
        private final StringBuilder builder = new StringBuilder();

        private final ArrayList<ExpressionNode> nodes = new ArrayList<>();
        private int currentStart = offset;

        public void submit() {
            var literal = builder.toString();
            builder.setLength(0);
            nodes.add(new StringLiteralNode(source.createSection(currentStart, offset - currentStart), literal));
            currentStart = offset;
        }

        public void submit(ExpressionNode expr) {
            nodes.add(expr);
            currentStart = offset;
        }

        public StringBuilder getBuilder() {
            return builder;
        }

        public StringNode finish(SourceSection section) {
            if (nodes.isEmpty() && builder.length() > 0) {
                return new StringLiteralNode(section, builder.toString());
            }
            if (!nodes.isEmpty()) {
                if (builder.length() > 0) {
                    submit();
                }
                return new StringInterpolationNode(section, nodes.toArray(ExpressionNode[]::new));
            }
            return null;
        }

    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T withContext(Class<?> tag, Callable<T> action) throws SelfishSyntaxError {
        var before = offset;
        eatWhitespace();
        var after = offset;
        var lookUp = table.check(offset, tag);
        var success = false;
        try {
            if (lookUp.isFailure()) {
                throw lookUp.getError();
            } else if (lookUp.isNotParsed()) {
                try {
                    var node = action.call();
                    table.putSuccess(after, node);
                    success = true;
                    return node;
                } catch (SelfishSyntaxError error) {
                    table.putFailure(after, tag, error);
                    throw error;
                } catch (Exception e) {
                    throw new SelfishSyntaxError(e.getMessage());
                }
            } else {
                success = true;
                return (T) lookUp.getNode();
            }
        } finally {
            if (!success) {
                offset = before;
            }
        }
    }

    public BarewordNode parseBareword() throws SelfishSyntaxError {
        return withContext(BarewordNode.class, () -> {
            final var start = offset;
            final var builder = new StringBuilder();
            var codepoint = checkCodePoint(currentCodepoint());
            var needWildcardExpansion = false;
            var needTildeExpansion = false;
            try {
                while (codepoint != INVALID && codepoint != ENDING_HINT) {
                    switch (codepoint) {
                        case WILDCARD:
                            builder.append('*');
                            needWildcardExpansion = true;
                            break;
                        case TILDE:
                            if (builder.length() == 0) {
                                builder.append('~');
                                needTildeExpansion = true;
                            } else {
                                throw new SelfishSyntaxError("tilde symbol can only be placed at the beginning of a bareword");
                            }
                            break;
                        default:
                            builder.appendCodePoint(codepoint);
                    }
                    moveNextCodePoint();
                    codepoint = checkCodePoint(currentCodepoint());
                }
                if (codepoint == INVALID) {
                    throw new SelfishSyntaxError("invalid character in bareword with codepoint: " + codepoint);
                }
            } catch (IndexOutOfBoundsException ignored) {
            }
            if (builder.length() == 0) {
                throw new SelfishSyntaxError("unexpected empty bareword");
            }
            return BarewordNodeGen.create(
                    source.createSection(start, this.offset - start),
                    builder.toString(),
                    needWildcardExpansion,
                    needTildeExpansion);
        });
    }

    private StringLiteralNode singleQuotedSubroutine() throws SelfishSyntaxError {
        final var start = offset - 1;
        var foundQuote = false;
        final var builder = new StringBuilder();
        try {
            while (!foundQuote || currentChar() == '\'') {
                if (currentChar() == '\'') {
                    if (foundQuote) {
                        builder.append('\'');
                        foundQuote = false;
                    } else {
                        foundQuote = true;
                    }
                } else {
                    builder.append(currentChar());
                }
                moveNextChar();
            }
        } catch (IndexOutOfBoundsException e) {
            if (!foundQuote) {
                throw new SelfishSyntaxError("unexpected EOI while parsing single quoted string", true);
            }
        }
        return new StringLiteralNode(source.createSection(start, offset - start), builder.toString());
    }

    private static final int EXPR_REDIRECT = -1;
    private static final int EXPR_ATOMIC = 0;
    private static final int EXPR_PIPELINE = 1;
    private static final int EXPR_CONTINUOUS = 2;
    private static final int EXPR_BACKGROUND = 3;

    private final static class OperatorStack {
        private int[] data = new int[16];
        private int size = 0;

        private void grow() {
            int[] newData = new int[data.length * 2];
            System.arraycopy(data, 0, newData, 0, data.length);
            data = newData;
        }

        public int top() {
            return data[size - 1];
        }

        public void push(int op) {
            if (size == data.length) grow();
            data[size] = op;
            size += 1;
        }

        public int pop() {
            size--;
            return data[size];
        }

        public boolean empty() {
            return size == 0;
        }
    }

    private boolean eatWhitespaceChecked(boolean inParen) throws SelfishSyntaxError, IndexOutOfBoundsException {
        var continuousLine = false;
        while (Character.isWhitespace(currentChar()) || currentChar() == '#' || continuousLine) {
            if (currentChar() == '#') {
                moveNextLine();
                if (!inParen && !continuousLine) {
                    return true;
                }
                continuousLine = false;
                continue;
            } else if (!continuousLine && currentChar() == '\\') {
                continuousLine = true;
            } else if (currentChar() == '\n') {
                if (!inParen && !continuousLine) {
                    moveNextChar();
                    return true;
                }
                continuousLine = false;
            } else if (!continuousLine && currentChar() == ')') {
                break;
            } else
                throw new SelfishSyntaxError("multiline symbol followed by extraneous character; " +
                                             "it can only be followed immediately by a new line or a comment.");
            moveNextChar();
        }
        return false;
    }

    public ExpressionNode parseExpressionTerm() throws SelfishSyntaxError, IndexOutOfBoundsException {
        if (currentChar() == '\'' || currentChar() == '\"') return parseString();
        if (currentChar() == '(') return parseExpression(true);
        return parseBareword();
    }

    public int parseExprOperator() throws SelfishSyntaxError {
        if (currentChar() == '>' || currentChar() == '<') return EXPR_REDIRECT;
        if (Character.isDigit(currentChar()) && offset < this.data.length() && (this.data.charAt(offset + 1) == '>' || this.data.charAt(offset + 1) == '<')) {
            return EXPR_REDIRECT;
        }
        if (currentChar() == '&') {
            var count = 0;
            while (currentChar() == '&') {
                count++;
                moveNextChar();
            }
            if (count == 1) return EXPR_BACKGROUND;
            if (count == 2) return EXPR_CONTINUOUS;
            throw new SelfishSyntaxError("unknown expression operator");
        }

        if (currentChar() == '|') {
            var count = 0;
            while (currentChar() == '|') {
                count++;
                moveNextChar();
            }
            if (count == 1) return EXPR_PIPELINE;
            throw new SelfishSyntaxError("unknown expression operator");
        }


        return EXPR_ATOMIC;


    }

    public ExpressionNode parseExpression(boolean inParen) throws SelfishSyntaxError {
        final ArrayList<ExpressionNode> terms = new ArrayList<>();
        final OperatorStack opStack = new OperatorStack();
        if (inParen) {
            opStack.push('(');
        }
        try {
            eatWhitespaceChecked(inParen);
            terms.add(parseExpressionTerm());
        } catch (IndexOutOfBoundsException exp) {
            // check whether the current expression is finished.
            exp.printStackTrace();
        }
        return null;
    }

    private StringNode doubleQuotedSubroutine(String type, int closeCond) throws SelfishSyntaxError, InternalParserError {
        var escapeMode = 0L;
        var foundQuotes = 0;
        var noMove = false;
        var start = offset - closeCond;
        final var state = new StringState();
        try {
            while (foundQuotes != closeCond) {
                if (getEscapeMode(escapeMode) == ESCAPE_NONE && currentChar() == '"') {
                    foundQuotes += 1;
                } else {
                    while (foundQuotes > 0) {
                        state.getBuilder().append('"');
                        foundQuotes -= 1;
                    }
                    switch (getEscapeMode(escapeMode)) {
                        case ESCAPE_NONE:
                            if (currentChar() == '\\') {
                                escapeMode = resetEscapeMode(ESCAPE_START);
                            } else if (currentChar() == '$') {
                                state.submit();
                                moveNextChar();
                                state.submit(parseExpression(true));
                                noMove = true;
                            } else {
                                state.getBuilder().append(currentChar());
                            }
                            break;
                        case ESCAPE_START:
                            switch (currentChar()) {
                                case 'a':
                                    state.getBuilder().append('\u0007');
                                    escapeMode = resetEscapeMode(ESCAPE_NONE);
                                    break;
                                case 'b':
                                    state.getBuilder().append('\u0010');
                                    escapeMode = resetEscapeMode(ESCAPE_NONE);
                                    break;
                                case 'f':
                                    state.getBuilder().append('\u000c');
                                    escapeMode = resetEscapeMode(ESCAPE_NONE);
                                    break;
                                case 'n':
                                    state.getBuilder().append('\n');
                                    escapeMode = resetEscapeMode(ESCAPE_NONE);
                                    break;
                                case 'r':
                                    state.getBuilder().append('\r');
                                    escapeMode = resetEscapeMode(ESCAPE_NONE);
                                    break;
                                case 't':
                                    state.getBuilder().append('\t');
                                    escapeMode = resetEscapeMode(ESCAPE_NONE);
                                    break;
                                case 'v':
                                    state.getBuilder().append('\u000b');
                                    escapeMode = resetEscapeMode(ESCAPE_NONE);
                                    break;
                                case '\\':
                                    state.getBuilder().append('\\');
                                    escapeMode = resetEscapeMode(ESCAPE_NONE);
                                    break;
                                case '"':
                                    state.getBuilder().append('"');
                                    escapeMode = resetEscapeMode(ESCAPE_NONE);
                                    break;
                                case '$':
                                    state.getBuilder().append('$');
                                    escapeMode = resetEscapeMode(ESCAPE_NONE);
                                    break;
                                case 'x':
                                    escapeMode = resetEscapeMode(ESCAPE_HEX);
                                    break;
                                case 'u':
                                    escapeMode = resetEscapeMode(ESCAPE_SMALL);
                                    break;
                                case 'U':
                                    escapeMode = resetEscapeMode(ESCAPE_LARGE);
                                    break;
                                default:
                                    if (currentChar() >= '0' && currentChar() <= '7') {
                                        escapeMode = resetEscapeMode(ESCAPE_OCTAL);
                                        escapeMode = addEscapeOct(escapeMode, currentChar());
                                    } else {
                                        throw new SelfishSyntaxError("unknown escape character: " + currentChar());
                                    }
                            }
                            break;
                        case ESCAPE_OCTAL:
                            if (getEscapeCount(escapeMode) == 3) {
                                noMove = true;
                                state.builder.appendCodePoint(getEscapeAccumulator(escapeMode));
                                escapeMode = resetEscapeMode(ESCAPE_NONE);
                            } else {
                                escapeMode = addEscapeOct(escapeMode, currentChar());
                            }
                            break;
                        case ESCAPE_HEX:
                            if (getEscapeCount(escapeMode) == 2) {
                                noMove = true;
                                state.builder.appendCodePoint(getEscapeAccumulator(escapeMode));
                                escapeMode = resetEscapeMode(ESCAPE_NONE);
                            } else {
                                escapeMode = addEscapeHex(escapeMode, currentChar());
                            }
                            break;
                        case ESCAPE_SMALL:
                            if (getEscapeCount(escapeMode) == 4) {
                                noMove = true;
                                state.builder.appendCodePoint(getEscapeAccumulator(escapeMode));
                                escapeMode = resetEscapeMode(ESCAPE_NONE);
                            } else {
                                escapeMode = addEscapeHex(escapeMode, currentChar());
                            }
                            break;
                        case ESCAPE_LARGE:
                            if (getEscapeCount(escapeMode) == 8) {
                                noMove = true;
                                state.builder.appendCodePoint(getEscapeAccumulator(escapeMode));
                                escapeMode = resetEscapeMode(ESCAPE_NONE);
                            } else {
                                escapeMode = addEscapeHex(escapeMode, currentChar());
                            }
                            break;
                        default:
                            throw new InternalParserError("entered unreachable escape mode");
                    }
                }
                if (!noMove) {
                    moveNextChar();
                }
                noMove = false;
            }
        } catch (IllegalArgumentException e) {
            throw new SelfishSyntaxError(e.getMessage());
        } catch (IndexOutOfBoundsException e) {
            throw new SelfishSyntaxError("unexpected EOI while parsing " + type, true);
        }
        return state.finish(source.createSection(start, offset - start));
    }

    public StringNode parseString() throws SelfishSyntaxError {
        return withContext(StringNode.class, () -> {
            var count = 0;
            if (currentChar() == '\'') {
                moveNextChar();
                return singleQuotedSubroutine();
            } else {
                while (currentChar() == '\"') {
                    count += 1;
                    moveNextChar();
                }
                switch (count) {
                    case 2:
                        return new StringLiteralNode(source.createSection(offset - 2, 2), "");
                    case 6:
                        return new StringLiteralNode(source.createSection(offset - 6, 6), "");
                    case 3:
                        return doubleQuotedSubroutine("heredoc", 3);
                    case 1:
                        return doubleQuotedSubroutine("double quoted string", 1);
                    default:
                        throw new SelfishSyntaxError("expected double quoted string, single quoted string or heredoc");
                }
            }
        });
    }

}
