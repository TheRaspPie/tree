package therasppie.tree.util;

public interface ByteArray extends PrimitiveArray
{
    byte nthByte(int i);

    ByteArray assocByte(int i, byte val);

    @Override
    default long nthPrim(int i)
    {
        return nthByte(i);
    }

    @Override
    default PrimitiveArray assocPrim(int i, long val)
    {
        return assocByte(i, (byte) val);
    }
}
