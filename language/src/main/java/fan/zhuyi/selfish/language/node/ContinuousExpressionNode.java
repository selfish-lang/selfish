package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.source.SourceSection;
import fan.zhuyi.selfish.language.utils.SelfishProcess;


public class ContinuousExpressionNode extends EvalExpressionNode {
    public ContinuousExpressionNode(SourceSection section, ExpressionNode[] nodes, SelfishProcess.IOPipe[] pipes, boolean background) {
        super(section, nodes, pipes, background);
    }
}
