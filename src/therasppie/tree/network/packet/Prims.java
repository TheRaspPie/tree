package therasppie.tree.network.packet;

import io.netty.buffer.ByteBuf;

public final class Prims
{
    private Prims()
    {
    }

    /* -- Reading -- */

    public static long readUInt32(ByteBuf buf)
    {
        return buf.readUnsignedInt();
    }

    public static long readUInt16(ByteBuf buf)
    {
        return (long) buf.readUnsignedShort();
    }

    public static long readUInt8(ByteBuf buf)
    {
        return (long) buf.readUnsignedByte();
    }

    public static long readInt64(ByteBuf buf)
    {
        return buf.readLong();
    }

    public static long readInt32(ByteBuf buf)
    {
        return (long) buf.readInt();
    }

    public static long readInt16(ByteBuf buf)
    {
        return (long) buf.readShort();
    }

    public static long readInt8(ByteBuf buf)
    {
        return (long) buf.readByte();
    }

    public static double readFloat64(ByteBuf buf)
    {
        return checkFinite(buf.readDouble());
    }

    public static double readFloat32(ByteBuf buf)
    {
        return checkFinite((double) buf.readFloat());
    }

    public static boolean readBool(ByteBuf buf)
    {
        return buf.readBoolean();
    }

    /* -- Writing -- */

    public static void writeUInt32(ByteBuf buf, long value)
    {
        if ((value & 0xffffffffL) != value)
        {
            throw new IllegalArgumentException("Value out of range for uint32: " + value);
        }
        buf.writeInt((int) value);
    }

    public static void writeUInt16(ByteBuf buf, long value)
    {
        if ((value & 0xffffL) != value)
        {
            throw new IllegalArgumentException("Value out of range for uint16: " + value);
        }
        buf.writeShort((int) value);
    }

    public static void writeUInt8(ByteBuf buf, long value)
    {
        if ((value & 0xffL) != value)
        {
            throw new IllegalArgumentException("Value out of range for uint8: " + value);
        }
        buf.writeByte((int) value);
    }

    public static void writeInt64(ByteBuf buf, long value)
    {
        buf.writeLong(value);
    }

    public static void writeInt32(ByteBuf buf, long value)
    {
        int cast = (int) value;
        if (cast != value)
        {
            throw new IllegalArgumentException("Value out of range for int32: " + value);
        }
        buf.writeInt(cast);
    }

    public static void writeInt16(ByteBuf buf, long value)
    {
        short cast = (short) value;
        if (cast != value)
        {
            throw new IllegalArgumentException("Value out of range for int16: " + value);
        }
        buf.writeShort((int) cast);
    }

    public static void writeInt8(ByteBuf buf, long value)
    {
        byte cast = (byte) value;
        if (cast != value)
        {
            throw new IllegalArgumentException("Value out of range for int8: " + value);
        }
        buf.writeByte((int) cast);
    }

    public static void writeFloat64(ByteBuf buf, double value)
    {
        checkFinite(value);
        buf.writeDouble(value);
    }

    public static void writeFloat32(ByteBuf buf, double value)
    {
        checkFinite(value);
        if (value < -Float.MAX_VALUE || value > Float.MAX_VALUE)
        {
            throw new IllegalArgumentException("Value out of range for float32: " + value);
        }
        buf.writeFloat((float) value);
    }

    public static void writeBool(ByteBuf buf, boolean value)
    {
        buf.writeBoolean(value);
    }

    /* -- Util -- */

    private static double checkFinite(double value)
    {
        if (!Double.isFinite(value))
        {
            throw new IllegalArgumentException("Not a finite floating point value: " + value);
        }
        return value;
    }
}
