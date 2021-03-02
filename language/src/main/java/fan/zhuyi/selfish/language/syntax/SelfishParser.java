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
        final int errOffset;

        public SelfishSyntaxError(String message) {
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

    private char testHex(char data) {
        if (data - '0' >= 0 && data - '0' <= 9) return (char) (data - '0');
        if (data - 'a' >= 0 && data - 'a' <= 5) return (char) (data - 'a' + 10);
        if (data - 'A' >= 0 && data - 'A' <= 5) return (char) (data - 'A' + 10);
        return Character.MAX_VALUE;
    }

    private void eatWhitespace() {
        try {
            while (Character.isWhitespace(currentChar()) || currentChar() == '#') {
                if (currentChar() == '#') {
                    moveNextLine();
                }
                moveNextChar();
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
                if (Character.charCount(codepoint) == 1 && ASCII.canEncode((char) codepoint)) {
                    return INVALID;
                }
                return codepoint;

        }
    }


    private static final int ESCAPE_NONE = 0b1;
    private static final int ESCAPE_NOTHING = 0b10;
    private static final int ESCAPE_OCTAL = 0b100;
    private static final int ESCAPE_HEX = 0b1000;
    private static final int ESCAPE_SMALL = 0b10000;
    private static final int ESCAPE_LARGE = 0b100000;

    private final class StringState {
        private final StringBuilder builder = new StringBuilder();

        private final ArrayList<ExpressionNode> nodes = new ArrayList<>();
        private final int start;
        private int currentStart = offset;

        StringState() {
            this.start = offset;
        }

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
                return new StringLiteralNode(source.createSection(start, offset - start), builder.toString());
            }
            if (!nodes.isEmpty()) {
                if (builder.length() > 0) {
                    submit();
                }
                return new StringInterpolationNode(source.createSection(start, offset - start), nodes.toArray(ExpressionNode[]::new));
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


}
