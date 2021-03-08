package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.source.SourceSection;
import fan.zhuyi.selfish.language.utils.SelfishProcess;


public class ContinuousExpressionNode extends EvalExpressionNode {
    private final boolean paralleled;

    public ContinuousExpressionNode(SourceSection section, ExpressionNode[] nodes, SelfishProcess.IOPipe[] pipes, boolean background, boolean paralleled) {
        super(section, nodes, pipes, background);
        this.paralleled = paralleled;
    }
}
