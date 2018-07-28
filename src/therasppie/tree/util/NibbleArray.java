package therasppie.tree.util;

import clojure.lang.Counted;

public interface NibbleArray extends Counted
{
    int nth(int i);

    static NibbleArray of(byte[] array)
    {
        return new NibbleArray()
        {
            @Override
            public int nth(int i)
            {
                int raw = Byte.toUnsignedInt(array[i / 2]);
                return (i & 1) == 0 ? raw & 0xf : raw >>> 4;
            }

            @Override
            public int count()
            {
                return array.length * 2;
            }
        };
    }
}
