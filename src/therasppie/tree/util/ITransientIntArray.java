package therasppie.tree.util;

public interface ITransientIntArray extends IntArray
{
    IPersistentIntArray persistent();

    @Override
    ITransientIntArray assocInt(int i, int val);
}
