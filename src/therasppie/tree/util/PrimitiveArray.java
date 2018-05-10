package therasppie.tree.util;

import clojure.lang.Counted;

public interface PrimitiveArray extends Counted
{
    long nthPrim(int i);

    default PrimitiveArray assocPrim(int i, long val)
    {
        throw new UnsupportedOperationException();
    }
}
