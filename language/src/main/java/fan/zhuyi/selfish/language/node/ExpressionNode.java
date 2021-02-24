package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public class ExpressionNode extends Node {

    SourceSection sourceSection;
    ExpressionNode(SourceSection section) {
        this.sourceSection = section;
    }
    public String executeString() {
        return null;
    };
    public long executeInteger() {
        throw new UnsupportedOperationException();
    };
    public double executeDouble() {
        throw new UnsupportedOperationException();
    }
    public double executeBigInteger() {
        throw new UnsupportedOperationException();
    }
    public double executeBigDecimal() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }
}
