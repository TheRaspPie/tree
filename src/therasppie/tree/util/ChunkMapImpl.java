package therasppie.tree.util;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;

@SuppressWarnings("unchecked")
public final class ChunkMapImpl extends Long2ReferenceOpenHashMap implements ChunkMap
{
    public ChunkMapImpl()
    {
        super();
    }

    @Override
    public Object put(Object key, Object value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object get(int x, int z, Object defaultChunk)
    {
        return getOrDefault(index(x, z), defaultChunk);
    }

    @Override
    public Object assoc(int x, int z, Object chunk)
    {
        return put(index(x, z), chunk);
    }

    @Override
    public Object dissoc(int x, int z)
    {
        return remove(index(x, z));
    }

    public static void copy(ChunkMapImpl src, ChunkMapImpl dst)
    {
        dst.clear();
        dst.putAll(src);
    }

    private static long index(int x, int z)
    {
        return ((long) x) << 32L | ((long) z) & 0xffffffffL;
    }
}
