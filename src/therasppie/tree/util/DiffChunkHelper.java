package therasppie.tree.util;

public final class DiffChunkHelper
{
    private DiffChunkHelper()
    {
    }

    public static void diffChunks(
        int x1, int y1, int x2, int y2, int vd,
        IntIntConsumer removeChunk, IntIntConsumer addChunk, IntIntConsumer diffChunk)
    {
        int length = 2 * vd + 1;
        int dx = x2 - x1;
        int dy = y2 - y1;
        int xs = Math.min(Math.abs(dx), length);
        int ys = Math.min(Math.abs(dy), length);
        int signDx = dx < 0 ? -1 : 1;
        int signDy = dy < 0 ? -1 : 1;

        for (int x = 0; x < xs; x++)
        {
            int remX = x1 + signDx * (x - vd);
            int addX = x2 + signDx * (vd - x);
            for (int y = 0; y < length; y++)
            {
                int remY = y1 + signDy * (y - vd);
                int addY = y2 + signDy * (vd - y);
                removeChunk.accept(remX, remY);
                addChunk.accept(addX, addY);
            }
        }

        for (int x = xs; x < length; x++)
        {
            int remX = x1 + signDx * (x - vd);
            int addX = x2 + signDx * (vd - x);
            for (int y = 0; y < ys; y++)
            {
                int remY = y1 + signDy * (y - vd);
                int addY = y2 + signDy * (vd - y);
                removeChunk.accept(remX, remY);
                addChunk.accept(addX, addY);
            }
        }

        for (int x = xs; x < length; x++)
        {
            int diffX = x1 + signDx * (x - vd);
            for (int y = ys; y < length; y++)
            {
                int diffY = y1 + signDy * (y - vd);
                diffChunk.accept(diffX, diffY);
            }
        }
    }

    public interface IntIntConsumer
    {
        void accept(int x, int y);
    }
}
