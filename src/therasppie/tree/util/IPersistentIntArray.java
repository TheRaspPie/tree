package therasppie.tree.util;

public interface IPersistentIntArray extends IntArray
{
    int PRIM_SHIFT = 6;

    ITransientIntArray asTransient();

    @Override
    IPersistentIntArray assocInt(int i, int val);
}
