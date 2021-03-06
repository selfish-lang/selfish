package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public class StringLiteralNode extends StringNode {
    String literal;

    public StringLiteralNode(SourceSection section, String literal) {
        super(section);
        this.literal = literal;
    }

    @Override
    public String executeString(VirtualFrame frame) {
        return literal;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return executeString(frame);
    }

}
