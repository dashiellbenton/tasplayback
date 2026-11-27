package dev.dbenton.tasplayback.core.json;

import dev.dbenton.tasplayback.core.io.FormatException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test
    void roundTripsNestedValues() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "quote\" backslash\\ newline\n tab\t unicode é \u0001");
        m.put("i", 123456789012L);
        m.put("neg", -7L);
        m.put("d", 0.1);
        m.put("whole", 2.0);
        m.put("b", true);
        m.put("n", null);
        m.put("list", Arrays.asList(1L, "two", false, new LinkedHashMap<>()));
        m.put("empty", Arrays.asList());
        Object back = Json.parse(Json.write(m));
        assertEquals(m, back);
    }

    @Test
    void parsesNumbersAndEscapes() {
        assertEquals(-12L, Json.parse("-12"));
        assertEquals(1.5e3, Json.parse("1.5e3"));
        assertEquals(1e30, Json.parse("1000000000000000000000000000000"));
        assertEquals("A/é", Json.parse("\"\\u0041\\/\\u00e9\""));
        assertEquals(Arrays.asList(1L, 2L), Json.parse(" [ 1 , 2 ] "));
    }

    @Test
    void rejectsMalformedInput() {
        for (String bad : new String[] { "", "{", "[1,]", "{\"a\" 1}", "tru", "01", "1.", "\"abc", "{}x", "\"\\q\"", "[1 2]", "-" }) {
            assertThrows(FormatException.class, () -> Json.parse(bad), bad);
        }
    }

    @Test
    void typedAccessorsReportMissingAndMistypedFields() {
        Json.Obj o = new Json.Obj(Json.parseObject("{\"a\": 1, \"s\": \"x\", \"l\": [{\"k\": 2}]}"));
        assertEquals(1, o.integer("a"));
        assertEquals("x", o.str("s"));
        assertEquals(9, o.integer("missing", 9));
        List<Json.Obj> l = o.objList("l");
        assertEquals(2, l.get(0).integer("k"));
        FormatException e = assertThrows(FormatException.class, () -> o.str("a"));
        assertTrue(e.getMessage().contains("$.a"));
        assertThrows(FormatException.class, () -> o.integer("nope"));
    }
}
