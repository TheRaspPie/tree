package therasppie.tree.util;

public interface ITransientByteArray extends ByteArray
{
    IPersistentByteArray persistent();

    @Override
    ITransientByteArray assocByte(int i, byte val);
}
