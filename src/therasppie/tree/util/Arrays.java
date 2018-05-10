package therasppie.tree.util;

public final class Arrays
{
    private Arrays()
    {
    }

    public static PrimitiveArray asNibbleArray(ByteArray byteArray)
    {
        return new PrimitiveArray()
        {
            @Override
            public long nthPrim(int i)
            {
                int raw = Byte.toUnsignedInt(byteArray.nthByte(i / 2));
                return (i & 1) == 0 ? raw & 0xf : raw >>> 4;
            }

            @Override
            public int count()
            {
                return byteArray.count() * 2;
            }
        };
    }

    public static ByteArray wrappedArray(byte[] array)
    {
        return new ByteArray()
        {
            @Override
            public byte nthByte(int i)
            {
                return array[i];
            }

            @Override
            public ByteArray assocByte(int i, byte val)
            {
                array[i] = val;
                return this;
            }

            @Override
            public int count()
            {
                return array.length;
            }
        };
    }

    public static IntArray wrappedArray(int[] array)
    {
        return new IntArray()
        {
            @Override
            public int nthInt(int i)
            {
                return array[i];
            }

            @Override
            public IntArray assocInt(int i, int val)
            {
                array[i] = val;
                return this;
            }

            @Override
            public int count()
            {
                return array.length;
            }
        };
    }

    public static ByteArray copyOnWriteByteArray(int count)
    {
        return copyOnWrite(new byte[count]);
    }

    public static ByteArray copyOnWriteByteArray(byte[] array)
    {
        return copyOnWrite(array.clone());
    }

    private static ByteArray copyOnWrite(byte[] array)
    {
        return new ByteArray()
        {
            @Override
            public byte nthByte(int i)
            {
                return array[i];
            }

            @Override
            public ByteArray assocByte(int i, byte val)
            {
                byte[] newArray = array.clone();
                newArray[i] = val;
                return copyOnWrite(newArray);
            }

            @Override
            public int count()
            {
                return array.length;
            }
        };
    }
}
