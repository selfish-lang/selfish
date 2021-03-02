package fan.zhuyi.selfish.language.syntax;

import com.oracle.truffle.api.nodes.Node;
import org.graalvm.collections.Pair;

import java.util.HashMap;

public class SelfishParserTable {

    public final static class ParsedState {

        final static ParsedState NOT_PARSED = new ParsedState();
        private final Node node;
        private final SelfishParser.SelfishSyntaxError error;

        private ParsedState() {
            this.node = null;
            this.error = null;
        }

        public ParsedState(Node node) {
            this.node = node;
            this.error = null;
        }

        public ParsedState(SelfishParser.SelfishSyntaxError error) {
            this.node = null;
            this.error = error;
        }

        public boolean isNotParsed() {
            return this == NOT_PARSED;
        }

        public boolean isFailure() {
            return this != NOT_PARSED && node == null;
        }

        public SelfishParser.SelfishSyntaxError getError() {
            return error;
        }

        Node getNode() {
            return node;
        }

    }

    private final HashMap<Pair<Class<?>, Integer>, ParsedState> cache = new HashMap<>();

    public ParsedState check(int offset, Class<?> tag) {
        return cache.getOrDefault(Pair.create(tag, offset), ParsedState.NOT_PARSED);
    }

    public void putSuccess(int offset, Node node) {
        cache.put(Pair.create(node.getClass(), offset), new ParsedState(node));
    }

    public void putFailure(int offset, Class<?> tag, SelfishParser.SelfishSyntaxError error) {
        cache.put(Pair.create(tag, offset), new ParsedState(error));
    }
}
