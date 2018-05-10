package therasppie.tree.util;

public interface ITransientCharArray extends CharArray
{
    IPersistentCharArray persistent();

    @Override
    ITransientCharArray assocChar(int i, char val);
}
