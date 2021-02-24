package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;

public class StringInterpolationNode extends StringNode {
    @Children
    ExpressionNode[] stringNodes;

    public StringInterpolationNode(SourceSection section, ExpressionNode[] stringNodes) {
        super(section);
        this.stringNodes = stringNodes;
    }

    @ExplodeLoop
    @Override
    public String executeString() {
        var builder = new StringBuilder();
        for (var i : stringNodes) {
            builder.append(i.executeString());
        }
        return builder.toString();
    }
}
