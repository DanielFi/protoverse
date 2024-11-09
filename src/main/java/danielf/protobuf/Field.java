package danielf.protobuf;

public class Field {
    private String name;
    private String type;
    private boolean repeated;

    public Field(String name, String type, boolean repeated) {
        this.name = name;
        this.type = type;
        this.repeated = repeated;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (this.repeated) {
            builder.append("repeated ");
        } else {
            builder.append("optional ");
        }
        builder.append(this.type);
        builder.append(" ");
        builder.append(this.name);
        return builder.toString();
    }
}
