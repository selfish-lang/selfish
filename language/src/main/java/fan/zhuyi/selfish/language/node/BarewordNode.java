package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import java.nio.file.Paths;

public abstract class BarewordNode extends ExpressionNode {
    String bareword;

    BarewordNode(SourceSection section) {
        super(section);
    }

    protected boolean needTildeExpansion() {
        return bareword.charAt(0) == '~';
    }

    protected boolean needWildCardExpansion() {
        /* use context please*/
        return bareword.contains("*");
    }

    protected static String getCurrentUser() {
        return System.getenv("USER");
    }

    protected static String currentWorkingDirectory() {
        return Paths.get(".").toAbsolutePath().normalize().toString();
    }

    @Specialization(guards = {"!needTildeExpansion()", "!needWildCardExpansion()"})
    public String executeString(VirtualFrame frame) {
        return bareword;
    }


    @Specialization
    @SuppressWarnings("unused")
    public String executeString(VirtualFrame frame,
                                @Cached("expandedString(frame)") String expanded,
                                @Cached("getCurrentUser()") String user,
                                @Cached("currentWorkingDirectory()") String cwd) {
        if (user.equals(getCurrentUser()) && cwd.equals(currentWorkingDirectory())) {
            return expanded;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return expandedString(frame);
        }
    }

    public String expandedString(VirtualFrame frame) {
        return "later";
    }

}
