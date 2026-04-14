public enum MessageType {
    // Explicit mapping between byte and enum
    //  → prevents breaking protocol when enum order changes.
    ECHO((byte) 1),
    P_REG((byte) 2),    //Producer register
    P_CM((byte) 3),    // Producer consume message 
    // Response
    R_ECHO((byte) 101),
    R_P_REG((byte) 102),
    R_P_CM((byte) 103);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static MessageType fromByte(byte b) {
        for (MessageType t : values()) {
            if (t.code == b) return t;
        }
        return null;
    }
}
