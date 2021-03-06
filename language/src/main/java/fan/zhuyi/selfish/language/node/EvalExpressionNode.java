package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import fan.zhuyi.selfish.language.utils.SelfishProcess;

public class EvalExpressionNode extends ExpressionNode {
    @Children
    protected final ExpressionNode[] nodes;

    protected final SelfishProcess.IOPipe[] pipes;

    protected final boolean background;

    public EvalExpressionNode(SourceSection section, ExpressionNode[] nodes, SelfishProcess.IOPipe[] pipes, boolean background) {
        super(section);
        this.nodes = nodes;
        this.pipes = pipes;
        this.background = background;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        /// TODO: implement this
        return null;
    }
}
