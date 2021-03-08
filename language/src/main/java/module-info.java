module fan.zhuyi.selfish.language {
    requires org.graalvm.truffle;
    requires java.desktop;


    provides com.oracle.truffle.api.TruffleLanguage.Provider
            with fan.zhuyi.selfish.language.SelfishLanguageProvider;

    exports fan.zhuyi.selfish.language;
    exports fan.zhuyi.selfish.language.runtime;
    exports fan.zhuyi.selfish.language.syntax;
    exports fan.zhuyi.selfish.language.utils;
    exports fan.zhuyi.selfish.language.node;
}
