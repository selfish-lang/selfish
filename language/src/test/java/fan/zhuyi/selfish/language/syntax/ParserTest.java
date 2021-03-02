package fan.zhuyi.selfish.language.syntax;

import com.oracle.truffle.api.source.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ParserTest {

//    private static void testRawStr(String source, String value, String range) {
//        var parser = new Se(Source.newBuilder("test", source, "test").build());
//        var result = parser.parseString();
//        assertTrue(result.exists(x -> x instanceof StringLiteralNode));
//        assertTrue(result.exists(x -> x.executeString(null).equals(value)));
//        assertTrue(result.exists(x -> x.getSourceSection().getCharacters().toString().equals(range == null ? source : range)));
//    }

    private static void testBareword(String source, String value, String range) {
        assertDoesNotThrow(() -> {
            var parser = new SelfishParser(Source.newBuilder("test", source, "test").build());
            var result = parser.parseBareword();
            assertNotNull(result);
            assertEquals(value, result.executeString(null));
            assertEquals(range == null ? source : range, result.getSourceSection().getCharacters());
        });
    }


//    @Test
//    public void parseRawString() {
//        testRawStr("'123'", "123", null);
//        testRawStr("'123''123'", "123'123", null);
//        testRawStr("\"123\"", "123", null);
//        testRawStr("\"123\\n\"", "123\n", null);
//        testRawStr("\"123\\v\"", "123\u000b", null);
//        testRawStr("\"\"\" \"123\" \"\"\"", " \"123\" ", null);
//        testRawStr("\"\\x12\"", "\u0012", null);
//        testRawStr(" \"\\x12 \" ", "\u0012 ", "\"\\x12 \"");
//        testRawStr("\"\\U00001234您\"", "\u1234您", null);
//        testRawStr("\"\\$(1)\\U0001F600\"", "$(1)\uD83D\uDE00", null);
//        testRawStr("\"\uD834\uDD1E𝄞\"", "\uD834\uDD1E𝄞", null);
//    }


    @Test
    public void parseBareword() {
        testBareword("~213/123", "~213/123", null);
        testBareword("𝄞", "𝄞", null);
        testBareword("@@@@", "@@@@", null);
    }

}
