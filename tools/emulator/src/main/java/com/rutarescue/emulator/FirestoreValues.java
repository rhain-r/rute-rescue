package com.rutarescue.emulator;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/** Encodes plain Java values into the Firestore REST "Value" JSON format. */
final class FirestoreValues {

    private FirestoreValues() {
    }

    static JsonObject encodeFields(Map<String, Object> data) {
        JsonObject fields = new JsonObject();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            fields.add(entry.getKey(), encode(entry.getValue()));
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    static JsonObject encode(Object value) {
        JsonObject out = new JsonObject();
        if (value == null) {
            out.add("nullValue", JsonNull.INSTANCE);
        } else if (value instanceof String) {
            out.addProperty("stringValue", (String) value);
        } else if (value instanceof Boolean) {
            out.addProperty("booleanValue", (Boolean) value);
        } else if (value instanceof Integer || value instanceof Long) {
            out.addProperty("integerValue", String.valueOf(value));
        } else if (value instanceof Number) {
            out.addProperty("doubleValue", ((Number) value).doubleValue());
        } else if (value instanceof List) {
            JsonArray values = new JsonArray();
            for (Object item : (List<Object>) value) values.add(encode(item));
            JsonObject array = new JsonObject();
            array.add("values", values);
            out.add("arrayValue", array);
        } else if (value instanceof Map) {
            JsonObject map = new JsonObject();
            map.add("fields", encodeFields((Map<String, Object>) value));
            out.add("mapValue", map);
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
        }
        return out;
    }
}
