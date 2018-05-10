package therasppie.tree.util;

public interface ChunkMap
{
    Object get(int x, int z, Object defaultChunk);

    Object assoc(int x, int z, Object chunk);

    Object dissoc(int x, int z);
}
