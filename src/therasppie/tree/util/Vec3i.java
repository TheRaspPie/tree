package therasppie.tree.util;

import clojure.lang.Indexed;

public final class Vec3i implements IntArray, Indexed
{
    public final int x;
    public final int y;
    public final int z;

    public Vec3i(int x, int y, int z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public Object nth(int i)
    {
        return nthInt(i);
    }

    @Override
    public Object nth(int i, Object notFound)
    {
        switch (i)
        {
            case 0: return x;
            case 1: return y;
            case 2: return z;
        }
        return notFound;
    }

    @Override
    public int nthInt(int i)
    {
        switch (i)
        {
            case 0: return x;
            case 1: return y;
            case 2: return z;
        }
        throw new IndexOutOfBoundsException();
    }

    @Override
    public IntArray assocInt(int i, int val)
    {
        switch (i)
        {
            case 0: return new Vec3i(val, y, z);
            case 1: return new Vec3i(x, val, z);
            case 2: return new Vec3i(x, y, val);
        }
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int count()
    {
        return 3;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
        {
            return true;
        }
        if (obj instanceof Vec3i)
        {
            Vec3i vec = (Vec3i) obj;
            return vec.x == x && vec.y == y && vec.z == z;
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return 97 * (211 * z + x) + y;
    }

    @Override
    public String toString()
    {
        return "[" + x + " " + y + " " + z + "]";
    }
}
