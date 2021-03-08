package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;


@TypeSystemReference(SelfishTypes.class)
public abstract class ExpressionNode extends Node {


    SourceSection sourceSection;

    ExpressionNode(SourceSection section) {
        this.sourceSection = section;
    }

    public String executeString(VirtualFrame frame) {
        return null;
    }

    public double executeBigInteger(VirtualFrame frame) {
        throw new UnsupportedOperationException();
    }

    public abstract Object executeGeneric(VirtualFrame frame);

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @CompilerDirectives.CompilationFinal
    private String filtered = null;

    protected String filteredString(VirtualFrame frame) {
        if (filtered == null) {
            filtered = executeString(frame)
                    .chars()
                    .filter(c -> c != '_')
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
        }
        return filtered;
    }

    @CompilerDirectives.CompilationFinal
    private double cachedDouble = 0;
    @CompilerDirectives.CompilationFinal
    private boolean doubleCached = false;

    @SuppressWarnings("unused")
    public double executeDouble(VirtualFrame frame) {
        if (!doubleCached) {
            doubleCached = true;
            cachedDouble = Double.parseDouble(filteredString(frame));
        }
        return cachedDouble;
    }

    @CompilerDirectives.CompilationFinal
    private long cachedInteger = 0;
    @CompilerDirectives.CompilationFinal
    private boolean integerCached = false;

    @SuppressWarnings("unused")
    public long executeInteger(VirtualFrame frame) {
        if (!integerCached) {
            integerCached = true;
            cachedInteger = Long.decode(filteredString(frame));
        }
        return cachedInteger;
    }

}
