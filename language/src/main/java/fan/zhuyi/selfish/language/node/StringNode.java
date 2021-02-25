package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.source.SourceSection;

public abstract class StringNode extends ExpressionNode {
    StringNode(SourceSection section) {
        super(section);
    }
}
