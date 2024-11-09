package danielf.protobuf;

import java.util.HashMap;
import java.util.Map;

public class Message {
    private final String name;
    private final Map<Integer, Field> fields = new HashMap<>();

    public Message(String name) {
        this.name = name;
    }

    public void setField(int number, Field field) {
        assert !this.fields.containsKey(number);
        this.fields.put(number, field);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder("message ");
        builder.append(this.name);
        builder.append(" {\n");
        for (var fieldEntry : this.fields.entrySet()) {
            builder.append("  ");
            builder.append(fieldEntry.getValue().toString());
            builder.append(" = ");
            builder.append(fieldEntry.getKey());
            builder.append(";\n");

        }
        builder.append("}\n");
        return builder.toString();
    }
}
