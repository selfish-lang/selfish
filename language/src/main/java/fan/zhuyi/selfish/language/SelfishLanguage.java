package fan.zhuyi.selfish.language;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import fan.zhuyi.selfish.language.runtime.SelfishContext;

@TruffleLanguage.Registration(
        id = SelfishLanguage.ID,
        name = "selfish",
        defaultMimeType = SelfishLanguage.MIME_TYPE,
        characterMimeTypes = SelfishLanguage.MIME_TYPE,
        fileTypeDetectors = SelfishFileDetector.class
)
public class SelfishLanguage extends TruffleLanguage<SelfishContext> {
    public static final String MIME_TYPE = "application/x-selfish";
    public static final String ID = "selfish";
    public static volatile int counter;

    public SelfishLanguage() {
        //noinspection NonAtomicOperationOnVolatileField
        ++counter;
    }

    @Override
    protected SelfishContext createContext(Env env) {
        return null;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        throw new UnsupportedOperationException("language is not implemented yet");
    }
}