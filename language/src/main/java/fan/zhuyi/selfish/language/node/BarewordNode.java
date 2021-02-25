package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.CompilerDirectives;
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


    private static final class CachedData {
        String expanded;
        String user;
        String cwd;

        public CachedData(String expanded, String user, String cwd) {
        }
    }

    CachedData cache = null;

    @Specialization
    @SuppressWarnings("unused")
    public String executeStringCached(VirtualFrame frame) {
        if (cache == null || !cache.user.equals(getCurrentUser()) || !cache.cwd.equals(currentWorkingDirectory())) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            cache = new CachedData(expandedString(frame), getCurrentUser(), currentWorkingDirectory());
        }
        return cache.expanded;
    }

    public String expandedString(VirtualFrame frame) {
        return "later";
    }

}
