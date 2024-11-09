package danielf.protobuf;

public class Utf16ToInt32Stream {
    private final char[] input;
    private int index = 0;

    public Utf16ToInt32Stream(char[] input) {
        this.input = input;
    }

    public Utf16ToInt32Stream(String input) {
        this(input.toCharArray());
    }

    public int next() {
        int c1 = input[index++];
        if (c1 < 0xD800) return c1;
        int c2 = input[index++];
        if (c2 < 0xD800) return (c2 << 13) | (c1 & 0x1FFF);
        int c3 = input[index++];
        return (c3 << 26) | ((c2 & 0x1FFF) << 13) | (c1 & 0x1FFF);
    }
}
