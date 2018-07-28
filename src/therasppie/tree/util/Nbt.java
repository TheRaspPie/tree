package therasppie.tree.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.DataInput;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class Nbt
{
    private static final int END_TAG = 0;
    private static final int BYTE_TAG = 1;
    private static final int SHORT_TAG = 2;
    private static final int INT_TAG = 3;
    private static final int LONG_TAG = 4;
    private static final int FLOAT_TAG = 5;
    private static final int DOUBLE_TAG = 6;
    private static final int BYTE_ARRAY_TAG = 7;
    private static final int STRING_TAG = 8;
    private static final int LIST_TAG = 9;
    private static final int COMPOUND_TAG = 10;
    private static final int INT_ARRAY_TAG = 11;

    public static Object readTag(DataInput input, int id) throws IOException
    {
        switch (id)
        {
            case LONG_TAG: return input.readLong();
            case INT_TAG: return (long) input.readInt();
            case SHORT_TAG: return (long) input.readShort();
            case BYTE_TAG: return (long) input.readByte();
            case DOUBLE_TAG: return input.readDouble();
            case FLOAT_TAG: return (double) input.readFloat();
            case STRING_TAG: return input.readUTF();
            case COMPOUND_TAG: return readCompoundTag(input);
            case LIST_TAG: return readListTag(input);
            case BYTE_ARRAY_TAG: return readByteArray(input);
            case INT_ARRAY_TAG: return readIntArray(input);
            default: throw new IOException("Invalid tag id: " + id);
        }
    }

    public static int readId(DataInput input) throws IOException
    {
        return input.readByte();
    }

    public static Map<String, Object> readCompoundTag(DataInput input) throws IOException
    {
        var map = new Object2ObjectOpenHashMap<String, Object>();
        int id;
        while (END_TAG != (id = readId(input)))
        {
            String name = input.readUTF();
            Object tag = readTag(input, id);
            map.put(name, tag);
        }
        return map;
    }

    public static List<Object> readListTag(DataInput input) throws IOException
    {
        int elementId = readId(input);
        int size = input.readInt();
        if (size < 0)
        {
            throw new IOException("Negative list tag size: " + size);
        }

        var list = new ObjectArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            list.add(readTag(input, elementId));
        }
        return list;
    }

    public static byte[] readByteArray(DataInput input) throws IOException
    {
        int length = input.readInt();
        byte[] array = new byte[length];
        input.readFully(array);
        return array;
    }

    public static int[] readIntArray(DataInput input) throws IOException
    {
        int length = input.readInt();
        int[] array = new int[length];
        for (int i = 0; i < length; i++)
        {
            array[i] = input.readInt();
        }
        return array;
    }

    public static Map<String, Object> readNbt(DataInput input) throws IOException
    {
        int id = readId(input);
        if (COMPOUND_TAG != id)
        {
            throw new IOException("Expected compound tag, got id: " + id);
        }

        input.readUTF();
        return readCompoundTag(input);
    }
}
