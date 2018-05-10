package therasppie.tree.network.codec;

import io.netty.buffer.ByteBuf;

public class VarIntUtil
{
    private static final int VARINT_MAX_BYTES = 5;

    public static int readVarInt(ByteBuf buf)
    {
        int bitsRead = 0;
        int result = 0;
        int read;
        do
        {
            read = (int) buf.readByte();
            result |= (read & 0b01111111) << bitsRead;
            bitsRead += 7;
            if (bitsRead > 7 * VARINT_MAX_BYTES)
            {
                throw new RuntimeException("VarInt is too big");
            }
        }
        while ((read & 0b10000000) != 0);
        return result;
    }

    public static int readVarIntSoft(ByteBuf buf)
    {
        int readerIndex = buf.readerIndex();
        int bitsRead = 0;
        int result = 0;
        int read;
        do
        {
            if (!buf.isReadable())
            {
                buf.readerIndex(readerIndex);
                return 0; // buffer didn't contain complete data - try again later
            }

            read = (int) buf.readByte();
            result |= (read & 0b01111111) << bitsRead;
            bitsRead += 7;
            if (bitsRead > 7 * VARINT_MAX_BYTES)
            {
                throw new RuntimeException("VarInt is too big");
            }
        }
        while ((read & 0b10000000) != 0);
        return result;
    }

    public static void writeVarInt(ByteBuf buf, int value)
    {
        do
        {
            int temp = value;
            value >>>= 7;
            if (value != 0)
            {
                buf.writeByte(temp | 0b10000000);
            }
            else
            {
                buf.writeByte(temp & 0b01111111);
                break;
            }
        }
        while (true);
    }
}
