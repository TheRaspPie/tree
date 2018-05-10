package therasppie.tree.util;

public interface IntArray extends PrimitiveArray
{
    int nthInt(int i);

    IntArray assocInt(int i, int val);

    @Override
    default long nthPrim(int i)
    {
        return nthInt(i);
    }

    @Override
    default PrimitiveArray assocPrim(int i, long val)
    {
        return assocInt(i, (int) val);
    }
}
