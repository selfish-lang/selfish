package fan.zhuyi.selfish.language.syntax;

import com.oracle.truffle.api.source.Source;
import fan.zhuyi.selfish.language.node.StringLiteralNode;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class ParserTest {

    void testRawStr(String source, String value, String range) {
        var parser = new Parser(Source.newBuilder("test", source, "test").build());
        var result = parser.parseString();
        assertTrue(result.isLeft());
        var node = result.left().get();
        assertTrue(node instanceof StringLiteralNode);
        var literal = node.executeString(null);
        assertEquals(literal, value);
        assertEquals(range == null ? source : range, node.getSourceSection().getCharacters().toString());
    }

    @Test
    public void parseRawString() {
        testRawStr("'123'", "123", null);
        testRawStr("'123''123'", "123'123", null);
        testRawStr("\"123\"", "123", null);
        testRawStr("\"123\\n\"", "123\n", null);
        testRawStr("\"123\\v\"", "123\u000b", null);
        testRawStr("\"\"\" \"123\" \"\"\"",  " \"123\" ", null);
        testRawStr("\"\\x12\"",  "\u0012", null);
        testRawStr(" \"\\x12 \" ",  "\u0012 ", "\"\\x12 \"");
        testRawStr("\"\\U00001234您\"",  "\u1234您", null);
        testRawStr("\"\\$(1)\\U0001F600\"",  "$(1)\uD83D\uDE00", null);
    }
}
