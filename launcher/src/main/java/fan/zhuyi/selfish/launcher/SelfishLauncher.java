package fan.zhuyi.selfish.launcher;
import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;

import java.util.List;
import java.util.Map;

public class SelfishLauncher extends AbstractLanguageLauncher {

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        return null;
    }

    @Override
    protected void launch(Context.Builder contextBuilder) {

    }

    @Override
    protected String getLanguageId() {
        return null;
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {

    }

    public static void main(String[] args) {
        new SelfishLauncher().launch(args);
    }
}