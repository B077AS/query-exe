package io.github.b077as.queryexe.query.binary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class BinaryValueTest {

    enum Role { ADMIN }

    static class Base implements Serializable {
        private final String id = "base";
        long big = 9_007_199_254_740_993L;
    }

    static class User extends Base {
        private final String id = "user";
        boolean enabled = true;
        Integer age = 41;
        Role role = Role.ADMIN;
        Role again = Role.ADMIN;
        List<String> names = new ArrayList<>(List.of("a", "b"));
        Map<String, Object> attributes = new LinkedHashMap<>(Map.of("k", 1));
        Map<Integer, String> numbered = new ConcurrentHashMap<>(Map.of(7, "seven"));
        TreeSet<String> sorted = new TreeSet<>(List.of("z", "y"));
        List<String> frozen = Collections.unmodifiableList(new ArrayList<>(List.of("f")));
        Date created = new Date(0);
        Instant seen = Instant.parse("2026-01-02T03:04:05.000000006Z");
        Duration timeout = Duration.ofMinutes(30);
        char[] secret = {'p', 'w'};
        int[] numbers = {1, 2};
        byte[] key = {1, 2, 3};
        Object self;
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @Test
    void describesASerializedObject() throws Exception {
        User user = new User();
        user.self = user;

        BinaryValue value = BinaryValue.of(serialize(user));

        assertEquals("Java serialization", value.format());
        assertNull(value.note());
        JsonObject json = JsonParser.parseString(value.text()).getAsJsonObject();
        assertEquals(User.class.getName(), json.get("@class").getAsString());
        assertEquals("base", json.get("id").getAsString());
        assertEquals("user", json.get("id (" + User.class.getName() + ")").getAsString());
        assertEquals("9007199254740993", json.get("big").getAsString());
        assertTrue(json.get("enabled").getAsBoolean());
        assertEquals(41, json.get("age").getAsInt());
        assertEquals(Role.class.getName() + ".ADMIN", json.get("again").getAsString());
        assertEquals("[\"a\",\"b\"]", json.get("names").toString());
        assertEquals("{\"k\":1}", json.get("attributes").toString());
        assertEquals("[{\"key\":7,\"value\":\"seven\"}]", json.get("numbered").toString());
        assertEquals("[\"y\",\"z\"]", json.get("sorted").toString());
        assertTrue(json.get("frozen").toString().contains("[\"f\"]"));
        assertEquals("1970-01-01T00:00:00Z", json.get("created").getAsString());
        assertEquals("2026-01-02T03:04:05.000000006Z", json.get("seen").getAsString());
        assertEquals("PT30M", json.get("timeout").getAsString());
        assertEquals("pw", json.get("secret").getAsString());
        assertEquals("[1,2]", json.get("numbers").toString());
        assertEquals("010203", json.getAsJsonObject("key").get("hex").getAsString());
        assertTrue(json.get("self").getAsString().startsWith("(refers back to"));
    }

    @Test
    void damagedSerializedDataFallsBackToHex() throws Exception {
        byte[] whole = serialize(new User());
        for (int length = 4; length < whole.length; length += 7) {
            BinaryValue value = BinaryValue.of(Arrays.copyOf(whole, length));
            assertNotNull(value.hex());
        }
        BinaryValue cut = BinaryValue.of(Arrays.copyOf(whole, whole.length / 2));
        assertNull(cut.text());
        assertTrue(cut.note().startsWith("Could not decode"));

        Random random = new Random(1);
        for (int i = 0; i < 2_000; i++) {
            byte[] damaged = whole.clone();
            damaged[4 + random.nextInt(damaged.length - 4)] = (byte) random.nextInt();
            assertNotNull(BinaryValue.of(damaged).hex());
        }
    }

    @Test
    void readsTextAndCompressedText() throws Exception {
        byte[] json = "{\"a\":\"è\"}".getBytes(StandardCharsets.UTF_8);
        assertEquals("{\"a\":\"è\"}", BinaryValue.of(json).text());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(json);
        }
        BinaryValue zipped = BinaryValue.of(bytes.toByteArray());
        assertEquals("gzip · text", zipped.format());
        assertEquals("{\"a\":\"è\"}", zipped.text());
    }

    @Test
    void recognisesImagesAndPlainBinary() {
        BinaryValue png = BinaryValue.of(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 26, '\n'});
        assertTrue(png.image().startsWith("data:image/png;base64,"));

        BinaryValue other = BinaryValue.of(new byte[] {0, 1, 2, 'A'});
        assertEquals("binary", other.format());
        assertNull(other.text());
        assertEquals("00000000  00 01 02 41                                       ...A", other.hex());
    }
}
