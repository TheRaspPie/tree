package therasppie.tree.util;

public interface CharArray extends PrimitiveArray
{
    char nthChar(int i);

    CharArray assocChar(int i, char val);

    @Override
    default long nthPrim(int i)
    {
        return nthChar(i);
    }

    @Override
    default PrimitiveArray assocPrim(int i, long val)
    {
        return assocChar(i, (char) val);
    }
}
