package fan.zhuyi.selfish.language;

import com.oracle.truffle.api.TruffleFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;



public class SelfishFileDetector implements TruffleFile.FileTypeDetector {
    @Override
    public String findMimeType(TruffleFile file) throws IOException {
        String name = file.getName();
        if (name != null && name.endsWith(".slsh")) {
            return SelfishLanguage.MIME_TYPE;
        }
        if (file.isReadable()) {
            var reader =  file.newBufferedReader();
            var line = reader.readLine();
            if (line.startsWith("#!") && line.trim().endsWith("selfish")) {
                return SelfishLanguage.MIME_TYPE;
            }
        }
        return null;
    }

    @Override
    public Charset findEncoding(TruffleFile file) throws IOException {
        return StandardCharsets.UTF_8;
    }
}