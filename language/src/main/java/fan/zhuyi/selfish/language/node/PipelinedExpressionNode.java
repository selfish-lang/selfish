package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.source.SourceSection;

public class PipelinedExpressionNode extends EvalExpressionNode {
    public PipelinedExpressionNode(SourceSection section, ExpressionNode[] nodes, boolean background) {
        super(section, nodes, null, background);
    }
}
