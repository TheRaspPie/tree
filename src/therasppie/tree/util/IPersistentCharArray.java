package therasppie.tree.util;

public interface IPersistentCharArray extends CharArray
{
    int PRIM_SHIFT = 7;

    ITransientCharArray asTransient();

    @Override
    IPersistentCharArray assocChar(int i, char val);
}
