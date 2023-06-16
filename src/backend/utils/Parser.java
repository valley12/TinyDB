package backend.utils;

import java.nio.ByteBuffer;

public class Parser {

    public static long parserLong(byte[] buf){
        ByteBuffer buffer = ByteBuffer.wrap(buf,0, 8);
        return buffer.getLong();
    }

    public static byte[] long2Byte(long value){
        return ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(value).array();
    }

}
