package io.github.b077as.queryexe.query.binary;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * What the value viewer shows for a binary cell: the content read as text, as a described Java object or as an image
 * where it is one of those, and always a hex dump.
 *
 * @param format  what the bytes are, e.g. "Java serialization" or "gzip · text"
 * @param text    the readable content, or null when there is none
 * @param image   the image as a data URL, or null
 * @param hex     a hex dump of the stored bytes (the first {@link #HEX_LIMIT} of them)
 * @param size    how many bytes are stored
 * @param note    why something is missing or cut, or null
 */
public record BinaryValue(String format, String text, String image, String hex, int size, String note) {

    static final int HEX_LIMIT = 64 * 1024;
    /** Content larger than this (also after decompression) is not decoded: the page could not show it comfortably. */
    static final int DECODE_LIMIT = 5 * 1024 * 1024;

    public static BinaryValue of(byte[] bytes) {
        String hex = hexDump(bytes);
        String note = bytes.length > HEX_LIMIT ? "The hex view shows the first " + HEX_LIMIT / 1024 + " KB." : null;
        byte[] content = bytes;
        String prefix = "";
        if (isGzip(bytes)) {
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                content = in.readNBytes(DECODE_LIMIT + 1);
                prefix = "gzip · ";
            } catch (IOException e) {
                return new BinaryValue("gzip", null, null, hex, bytes.length, "Could not decompress: " + e.getMessage());
            }
        }
        if (content.length > DECODE_LIMIT) {
            return new BinaryValue(prefix + "binary", null, null, hex, bytes.length,
                    "Too large to decode. The hex view shows the first " + HEX_LIMIT / 1024 + " KB.");
        }

        String imageType = imageType(content);
        if (imageType != null) {
            String url = "data:image/" + imageType + ";base64," + Base64.getEncoder().encodeToString(content);
            return new BinaryValue(prefix + imageType.toUpperCase() + " image", null, url, hex, bytes.length, note);
        }
        if (SerializedStream.matches(content)) {
            try {
                return new BinaryValue(prefix + "Java serialization", SerializedStream.describe(content).toString(), null, hex, bytes.length, note);
            } catch (IOException | RuntimeException e) {
                String reason = e.getMessage() == null ? "the data is cut off" : e.getMessage();
                return new BinaryValue(prefix + "Java serialization", null, null, hex, bytes.length, "Could not decode: " + reason);
            }
        }
        String text = utf8(content);
        if (text != null) {
            return new BinaryValue(prefix + "text", text, null, hex, bytes.length, note);
        }
        return new BinaryValue(prefix + "binary", null, null, hex, bytes.length, note);
    }

    private static boolean isGzip(byte[] bytes) {
        return bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b;
    }

    private static String imageType(byte[] bytes) {
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', '\r', '\n')) {
            return "png";
        }
        if (startsWith(bytes, 0, 0xff, 0xd8, 0xff)) {
            return "jpeg";
        }
        if (startsWith(bytes, 0, 'G', 'I', 'F', '8')) {
            return "gif";
        }
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
            return "webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xff) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /** The bytes as text, or null when they are not valid UTF-8 or hold control characters text would not have. */
    private static String utf8(byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
        boolean binary = text.chars().anyMatch(c -> c < 0x20 && c != '\t' && c != '\n' && c != '\r' && c != '\f');
        return binary ? null : text;
    }

    /** Sixteen bytes per line: the offset, the bytes in hex, then the printable ones. */
    private static String hexDump(byte[] bytes) {
        byte[] shown = bytes.length > HEX_LIMIT ? Arrays.copyOf(bytes, HEX_LIMIT) : bytes;
        StringBuilder out = new StringBuilder(shown.length * 5);
        for (int line = 0; line < shown.length; line += 16) {
            if (line > 0) {
                out.append('\n');
            }
            out.append(String.format("%08x ", line));
            StringBuilder printable = new StringBuilder(16);
            for (int i = 0; i < 16; i++) {
                if (i % 8 == 0) {
                    out.append(' ');
                }
                if (line + i < shown.length) {
                    int b = shown[line + i] & 0xff;
                    out.append(Character.forDigit(b >> 4, 16)).append(Character.forDigit(b & 0xf, 16)).append(' ');
                    printable.append(b >= 0x20 && b < 0x7f ? (char) b : '.');
                } else {
                    out.append("   ");
                }
            }
            out.append(' ').append(printable);
        }
        return out.toString();
    }
}
