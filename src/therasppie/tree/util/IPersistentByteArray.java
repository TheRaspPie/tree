package therasppie.tree.util;

public interface IPersistentByteArray extends ByteArray
{
    int PRIM_SHIFT = 8;

    ITransientByteArray asTransient();

    @Override
    IPersistentByteArray assocByte(int i, byte val);
}
