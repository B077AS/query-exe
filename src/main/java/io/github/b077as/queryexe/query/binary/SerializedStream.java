package io.github.b077as.queryexe.query.binary;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Describes the content of a Java serialization stream as JSON, by reading the stream format itself.
 * <p>
 * Nothing is ever deserialized: no class is loaded and no code of the stored objects runs, so bytes from an untrusted
 * database are harmless and the application's classes are not needed. {@code ObjectInputStream} must not be used here.
 * <p>
 * The format is the "Object Serialization Stream Protocol" of the Java specification.
 */
final class SerializedStream {

    private static final int MAGIC = 0xACED;
    private static final int VERSION = 5;
    private static final int BASE_HANDLE = 0x7E0000;

    private static final int TC_NULL = 0x70;
    private static final int TC_REFERENCE = 0x71;
    private static final int TC_CLASSDESC = 0x72;
    private static final int TC_OBJECT = 0x73;
    private static final int TC_STRING = 0x74;
    private static final int TC_ARRAY = 0x75;
    private static final int TC_CLASS = 0x76;
    private static final int TC_BLOCKDATA = 0x77;
    private static final int TC_ENDBLOCKDATA = 0x78;
    private static final int TC_RESET = 0x79;
    private static final int TC_BLOCKDATALONG = 0x7A;
    private static final int TC_LONGSTRING = 0x7C;
    private static final int TC_PROXYCLASSDESC = 0x7D;
    private static final int TC_ENUM = 0x7E;

    private static final int SC_WRITE_METHOD = 0x01;
    private static final int SC_SERIALIZABLE = 0x02;
    private static final int SC_EXTERNALIZABLE = 0x04;
    private static final int SC_BLOCK_DATA = 0x08;

    /** Limits against damaged or hostile input. */
    private static final int MAX_DEPTH = 400;
    private static final int MAX_NODES = 200_000;

    private static final Set<String> BOXES = Set.of("java.lang.Boolean", "java.lang.Byte", "java.lang.Character",
            "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double");
    private static final Set<String> LISTS = Set.of("java.util.ArrayList", "java.util.LinkedList", "java.util.HashSet",
            "java.util.LinkedHashSet", "java.util.TreeSet", "java.util.ArrayDeque", "java.util.concurrent.CopyOnWriteArrayList");
    private static final Set<String> MAPS = Set.of("java.util.HashMap", "java.util.LinkedHashMap", "java.util.Hashtable",
            "java.util.TreeMap", "java.util.concurrent.ConcurrentHashMap");

    // ------------------------------------------------------------------------------------ what the stream holds

    private record Field(char type, String name) {
    }

    private static final class ClassDesc {
        String name;
        int flags;
        final List<Field> fields = new ArrayList<>();
        ClassDesc parent;
    }

    private static final class Instance {
        ClassDesc type;
        /** One entry per class of the hierarchy that has data, the topmost superclass first. */
        final List<ClassData> data = new ArrayList<>();
    }

    private static final class ClassData {
        ClassDesc type;
        final Map<String, Object> values = new LinkedHashMap<>();
        /** What the class's own writeObject/writeExternal added: objects and {@code byte[]} blocks. */
        final List<Object> extra = new ArrayList<>();
    }

    private record ArrayValue(String type, List<Object> items) {
    }

    private record EnumValue(String type, String name) {
    }

    private record ClassValue(String name) {
    }

    private static final Object END = new Object();

    // ------------------------------------------------------------------------------------ parsing

    private final DataInputStream in;
    private final List<Object> handles = new ArrayList<>();
    private int depth;

    private SerializedStream(byte[] bytes) {
        this.in = new DataInputStream(new ByteArrayInputStream(bytes));
    }

    static boolean matches(byte[] bytes) {
        return bytes.length >= 4 && (bytes[0] & 0xff) == 0xAC && (bytes[1] & 0xff) == 0xED;
    }

    /**
     * @return the stream's content; a stream holding several values becomes an array
     * @throws IOException when the bytes are cut off, damaged, or use a part of the format that cannot be read without the class
     */
    static JsonElement describe(byte[] bytes) throws IOException {
        SerializedStream stream = new SerializedStream(bytes);
        if (stream.in.readUnsignedShort() != MAGIC || stream.in.readUnsignedShort() != VERSION) {
            throw new IOException("Not a Java serialization stream");
        }
        List<Object> contents = new ArrayList<>();
        while (stream.in.available() > 0) {
            contents.add(stream.content(stream.in.readUnsignedByte()));
        }
        Renderer renderer = new Renderer();
        return contents.size() == 1 ? renderer.render(contents.get(0)) : renderer.renderAll(contents);
    }

    private Object content(int code) throws IOException {
        if (++depth > MAX_DEPTH) {
            throw new IOException("The data is nested too deeply");
        }
        try {
            return switch (code) {
                case TC_NULL -> null;
                case TC_REFERENCE -> reference();
                case TC_STRING -> remember(in.readUTF());
                case TC_LONGSTRING -> remember(new String(bytes(in.readLong()), StandardCharsets.UTF_8));
                case TC_OBJECT -> object();
                case TC_ARRAY -> array();
                case TC_ENUM -> enumConstant();
                case TC_CLASS -> {
                    ClassDesc type = classDesc();
                    yield remember(new ClassValue(type == null ? null : type.name));
                }
                case TC_CLASSDESC -> newClassDesc();
                case TC_PROXYCLASSDESC -> proxyClassDesc();
                case TC_BLOCKDATA -> bytes(in.readUnsignedByte());
                case TC_BLOCKDATALONG -> bytes(in.readInt());
                case TC_ENDBLOCKDATA -> END;
                case TC_RESET -> {
                    handles.clear();
                    yield content(in.readUnsignedByte());
                }
                default -> throw new IOException(String.format("Unsupported content (type 0x%02x)", code));
            };
        } finally {
            depth--;
        }
    }

    private <T> T remember(T value) {
        handles.add(value);
        return value;
    }

    private Object reference() throws IOException {
        int index = in.readInt() - BASE_HANDLE;
        if (index < 0 || index >= handles.size()) {
            throw new IOException("Broken back reference");
        }
        return handles.get(index);
    }

    private byte[] bytes(long length) throws IOException {
        if (length < 0 || length > in.available()) {
            throw new IOException("The data is cut off");
        }
        byte[] out = new byte[(int) length];
        in.readFully(out);
        return out;
    }

    private ClassDesc classDesc() throws IOException {
        Object value = content(in.readUnsignedByte());
        if (value == null || value instanceof ClassDesc) {
            return (ClassDesc) value;
        }
        throw new IOException("A class description was expected");
    }

    private ClassDesc newClassDesc() throws IOException {
        ClassDesc type = new ClassDesc();
        type.name = in.readUTF();
        in.readLong(); // serialVersionUID
        remember(type);
        type.flags = in.readUnsignedByte();
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            char code = (char) in.readUnsignedByte();
            String name = in.readUTF();
            if (code == '[' || code == 'L') {
                content(in.readUnsignedByte()); // the field's declared type
            }
            type.fields.add(new Field(code, name));
        }
        annotation();
        type.parent = classDesc();
        return type;
    }

    private ClassDesc proxyClassDesc() throws IOException {
        ClassDesc type = remember(new ClassDesc());
        int count = in.readInt();
        List<String> interfaces = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            interfaces.add(in.readUTF());
        }
        type.name = "proxy " + String.join(", ", interfaces);
        type.flags = SC_SERIALIZABLE;
        annotation();
        type.parent = classDesc();
        return type;
    }

    /** Everything up to the next end-of-block marker. */
    private List<Object> annotation() throws IOException {
        List<Object> out = new ArrayList<>();
        while (true) {
            Object value = content(in.readUnsignedByte());
            if (value == END) {
                return out;
            }
            out.add(value);
        }
    }

    private Instance object() throws IOException {
        Instance instance = new Instance();
        instance.type = classDesc();
        if (instance.type == null) {
            throw new IOException("An object without a class");
        }
        remember(instance);
        List<ClassDesc> hierarchy = new ArrayList<>();
        for (ClassDesc type = instance.type; type != null; type = type.parent) {
            if (hierarchy.size() > MAX_DEPTH) {
                throw new IOException("The class hierarchy is too deep");
            }
            hierarchy.add(type);
        }
        Collections.reverse(hierarchy);
        for (ClassDesc type : hierarchy) {
            ClassData data = new ClassData();
            data.type = type;
            if ((type.flags & SC_SERIALIZABLE) != 0) {
                for (Field field : type.fields) {
                    data.values.put(field.name(), value(field.type()));
                }
                if ((type.flags & SC_WRITE_METHOD) != 0) {
                    data.extra.addAll(annotation());
                }
            } else if ((type.flags & SC_EXTERNALIZABLE) != 0) {
                if ((type.flags & SC_BLOCK_DATA) == 0) {
                    throw new IOException(type.name + " is written in the old externalizable format, which only the class itself can read");
                }
                data.extra.addAll(annotation());
            }
            instance.data.add(data);
        }
        return instance;
    }

    private Object value(char type) throws IOException {
        return switch (type) {
            case 'B' -> in.readByte();
            case 'C' -> String.valueOf(in.readChar());
            case 'D' -> in.readDouble();
            case 'F' -> in.readFloat();
            case 'I' -> in.readInt();
            case 'J' -> in.readLong();
            case 'S' -> in.readShort();
            case 'Z' -> in.readBoolean();
            case 'L', '[' -> content(in.readUnsignedByte());
            default -> throw new IOException("Unknown field type " + type);
        };
    }

    private Object array() throws IOException {
        ClassDesc type = classDesc();
        if (type == null || type.name.length() < 2) {
            throw new IOException("An array without a class");
        }
        List<Object> items = new ArrayList<>();
        ArrayValue array = new ArrayValue(type.name, items);
        int index = handles.size();
        remember(array);
        int length = in.readInt();
        // every element takes at least one byte
        if (length < 0 || length > in.available()) {
            throw new IOException("The data is cut off");
        }
        char element = type.name.charAt(1);
        if (element == 'B') {
            byte[] bytes = bytes(length);
            handles.set(index, bytes);
            return bytes;
        }
        for (int i = 0; i < length; i++) {
            items.add(value(element));
        }
        return array;
    }

    private EnumValue enumConstant() throws IOException {
        ClassDesc type = classDesc();
        int index = handles.size();
        remember(null);
        Object name = content(in.readUnsignedByte());
        if (type == null || !(name instanceof String text)) {
            throw new IOException("A broken enum constant");
        }
        EnumValue value = new EnumValue(type.name, text);
        handles.set(index, value);
        return value;
    }

    // ------------------------------------------------------------------------------------ rendering

    /** Turns the parsed values into JSON, writing the common JDK classes the way they read rather than the way they are stored. */
    private static final class Renderer {

        /** The objects being written right now: meeting one again means the data refers to itself. */
        private final Set<Object> open = Collections.newSetFromMap(new IdentityHashMap<>());
        private int nodes;
        private int depth;

        JsonArray renderAll(List<?> values) {
            JsonArray out = new JsonArray();
            for (Object value : values) {
                out.add(render(value));
            }
            return out;
        }

        JsonElement render(Object value) {
            // shared objects are written out each time they appear, so the output needs its own limit
            if (++nodes > MAX_NODES || depth > MAX_DEPTH) {
                return new JsonPrimitive("… (not shown: too large)");
            }
            depth++;
            try {
                return switch (value) {
                    case null -> JsonNull.INSTANCE;
                    case String s -> new JsonPrimitive(s);
                    case Boolean b -> new JsonPrimitive(b);
                    case Double d when !Double.isFinite(d) -> new JsonPrimitive(d.toString());
                    case Float f when !Float.isFinite(f) -> new JsonPrimitive(f.toString());
                    case Number n -> new JsonPrimitive(n);
                    case byte[] b -> hex(b);
                    case EnumValue e -> new JsonPrimitive(e.type() + "." + e.name());
                    case ClassValue c -> new JsonPrimitive("class " + c.name());
                    case ClassDesc c -> new JsonPrimitive("class " + c.name);
                    case ArrayValue a -> array(a);
                    case Instance i -> instance(i);
                    default -> new JsonPrimitive(value.toString());
                };
            } finally {
                depth--;
            }
        }

        private JsonElement hex(byte[] bytes) {
            JsonObject out = new JsonObject();
            int shown = Math.min(bytes.length, 256);
            out.addProperty("@bytes", bytes.length);
            out.addProperty("hex", HexFormat.of().formatHex(bytes, 0, shown) + (shown < bytes.length ? "…" : ""));
            return out;
        }

        private JsonElement array(ArrayValue array) {
            if (!open.add(array)) {
                return new JsonPrimitive("(refers back to " + array.type() + ")");
            }
            try {
                if (array.type().equals("[C")) {
                    StringBuilder text = new StringBuilder();
                    array.items().forEach(text::append);
                    return new JsonPrimitive(text.toString());
                }
                return renderAll(array.items());
            } finally {
                open.remove(array);
            }
        }

        private JsonElement instance(Instance instance) {
            String name = instance.type.name;
            if (!open.add(instance)) {
                return new JsonPrimitive("(refers back to " + name + ")");
            }
            try {
                JsonElement known = wellKnown(name, instance);
                return known != null ? known : fields(instance);
            } finally {
                open.remove(instance);
            }
        }

        /** The readable form of a JDK class, or null to show its fields. */
        private JsonElement wellKnown(String name, Instance instance) {
            if (instance.data.isEmpty()) {
                return null;
            }
            ClassData own = instance.data.get(instance.data.size() - 1);
            if (BOXES.contains(name) && own.values.containsKey("value")) {
                return render(own.values.get("value"));
            }
            // a subclass has its own name, so only the exact classes are matched: its fields would be lost otherwise
            if (LISTS.contains(name) || MAPS.contains(name)) {
                List<Object> objects = new ArrayList<>();
                for (ClassData data : instance.data) {
                    data.extra.stream().filter(o -> !(o instanceof byte[])).forEach(objects::add);
                }
                if (LISTS.contains(name)) {
                    // TreeSet writes its comparator first
                    if (name.equals("java.util.TreeSet") && !objects.isEmpty()) {
                        objects.remove(0);
                    }
                    return renderAll(objects);
                }
                // ConcurrentHashMap ends its entries with a pair of nulls
                if (name.endsWith("ConcurrentHashMap") && objects.size() >= 2) {
                    objects = objects.subList(0, objects.size() - 2);
                }
                return map(objects);
            }
            byte[] block = own.extra.size() >= 1 && own.extra.get(0) instanceof byte[] b ? b : null;
            if (name.equals("java.util.Date") && block != null && block.length == 8) {
                return new JsonPrimitive(Instant.ofEpochMilli(ByteBuffer.wrap(block).getLong()).toString());
            }
            // java.time values share one stand-in class: a type byte followed by the value
            if (name.equals("java.time.Ser") && block != null && block.length == 13 && (block[0] == 1 || block[0] == 2)) {
                ByteBuffer buffer = ByteBuffer.wrap(block, 1, 12);
                long seconds = buffer.getLong();
                int nanos = buffer.getInt();
                try {
                    return new JsonPrimitive(block[0] == 1
                            ? Duration.ofSeconds(seconds, nanos).toString()
                            : Instant.ofEpochSecond(seconds, nanos).toString());
                } catch (RuntimeException outOfRange) {
                    return null;
                }
            }
            return null;
        }

        private JsonElement map(List<Object> keysAndValues) {
            boolean textKeys = true;
            for (int i = 0; i + 1 < keysAndValues.size(); i += 2) {
                textKeys &= keysAndValues.get(i) instanceof String;
            }
            if (textKeys) {
                JsonObject out = new JsonObject();
                for (int i = 0; i + 1 < keysAndValues.size(); i += 2) {
                    out.add((String) keysAndValues.get(i), render(keysAndValues.get(i + 1)));
                }
                return out;
            }
            JsonArray out = new JsonArray();
            for (int i = 0; i + 1 < keysAndValues.size(); i += 2) {
                JsonObject entry = new JsonObject();
                entry.add("key", render(keysAndValues.get(i)));
                entry.add("value", render(keysAndValues.get(i + 1)));
                out.add(entry);
            }
            return out;
        }

        private JsonElement fields(Instance instance) {
            JsonObject out = new JsonObject();
            out.addProperty("@class", instance.type.name);
            for (ClassData data : instance.data) {
                data.values.forEach((field, value) ->
                        // a subclass may reuse a field name of its superclass
                        out.add(out.has(field) ? field + " (" + data.type.name + ")" : field, render(value)));
                if (!data.extra.isEmpty()) {
                    out.add(out.has("@data") ? "@data (" + data.type.name + ")" : "@data", renderAll(data.extra));
                }
            }
            return out;
        }
    }
}
