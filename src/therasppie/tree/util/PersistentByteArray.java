package therasppie.tree.util;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PersistentByteArray implements IPersistentByteArray
{
    private static final int NODE_SHIFT = 5;
    private static final int PRIM_SHIFT = IPersistentByteArray.PRIM_SHIFT;
    private static final byte[] EMPTY_PRIM_ARRAY = new byte[1 << PRIM_SHIFT];

    private final int count;
    private final int shift;
    private final Object arr;
    private int hashCode;

    private static void checkArg(boolean condition, String message)
    {
        if (!condition)
        {
            throw new IllegalArgumentException(message);
        }
    }

    private static int countToShift(int count)
    {
        return 32 - Integer.numberOfLeadingZeros(count - 1);
    }

    public static PersistentByteArray create(int count)
    {
        checkArg(count >= 0, "count must be >= 0");
        int shift = Math.max(PRIM_SHIFT, count == 0 ? 0 : countToShift(count));
        Object arr = EMPTY_PRIM_ARRAY;
        int level = shift - PRIM_SHIFT;
        while (level != 0)
        {
            int delta = Math.min(level, NODE_SHIFT);
            Object[] array = new Object[1 << delta];
            Arrays.fill(array, arr);
            arr = array;
            level -= delta;
        }
        return new PersistentByteArray(count, shift, arr);
    }

    PersistentByteArray(int count, int shift, Object arr)
    {
        this.count = count;
        this.shift = shift;
        this.arr = arr;
    }

    @Override
    public int count()
    {
        return count;
    }

    @Override
    public byte nthByte(int i)
    {
        Objects.checkIndex(i, count);
        Object arr = this.arr;
        int level = shift;
        while (level != PRIM_SHIFT)
        {
            Object[] array = (Object[]) arr;
            int count = array.length;
            level -= countToShift(count);
            arr = array[(i >>> level) & (count - 1)];
        }
        return ((byte[]) arr)[i & ((1 << PRIM_SHIFT) - 1)];
    }

    @Override
    public IPersistentByteArray assocByte(int i, byte val)
    {
        Objects.checkIndex(i, count);
        return new PersistentByteArray(count, shift, doAssoc(arr, shift, i, val));
    }

    private static Object doAssoc(Object arr, int level, int i, byte val)
    {
        if (level != PRIM_SHIFT)
        {
            Object[] array = ((Object[]) arr).clone();
            int count = array.length;
            level -= countToShift(count);
            int subidx = (i >>> level) & (count - 1);
            array[subidx] = doAssoc(array[subidx], level, i, val);
            return array;
        }
        byte[] array = ((byte[]) arr).clone();
        array[i & ((1 << PRIM_SHIFT) - 1)] = val;
        return array;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
        {
            return true;
        }
        else if (obj instanceof PersistentByteArray)
        {
            return specializedEquals(this, (PersistentByteArray) obj);
        }
        else if (obj instanceof IPersistentByteArray)
        {
            return genericEquals(this, (IPersistentByteArray) obj);
        }
        return false;
    }

    private static boolean genericEquals(IPersistentByteArray arr1, IPersistentByteArray arr2)
    {
        int count = arr1.count();
        if (count != arr2.count())
        {
            return false;
        }
        for (int i = 0; i < count; i++)
        {
            if (arr1.nthByte(i) != arr2.nthByte(i))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean specializedEquals(PersistentByteArray arr1, PersistentByteArray arr2)
    {
        return arr1.count == arr2.count && arraysEqual(arr1.arr, arr2.arr, arr1.shift);
    }

    private static boolean arraysEqual(Object arr1, Object arr2, int level)
    {
        if (arr1 == arr2)
        {
            return true;
        }
        if (level != PRIM_SHIFT)
        {
            Object[] array1 = (Object[]) arr1;
            Object[] array2 = (Object[]) arr2;
            int count = array1.length;
            level -= countToShift(count);
            for (int i = 0; i < count; i++)
            {
                if (!arraysEqual(array1[i], array2[i], level))
                {
                    return false;
                }
            }
            return true;
        }
        byte[] array1 = (byte[]) arr1;
        byte[] array2 = (byte[]) arr2;
        for (int i = 0; i < (1 << PRIM_SHIFT); i++)
        {
            if (array1[i] != array2[i])
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode()
    {
        int result = hashCode;
        if (result == 0)
        {
            hashCode = result = hash(this);
        }
        return result;
    }

    private static int hash(IPersistentByteArray arr)
    {
        int result = 1;
        for (int count = arr.count(), i = 0; i < count; i++)
        {
            result = 31 * result + arr.nthByte(i);
        }
        return result;
    }

    @Override
    public ITransientByteArray asTransient()
    {
        return new TransientByteArray(count, shift, arr);
    }

    public interface DiffResultConsumer
    {
        void different(int i, byte oldVal, byte newVal);
    }

    public static void diff(PersistentByteArray arr1, PersistentByteArray arr2, DiffResultConsumer diffResultConsumer)
    {
        checkArg(arr1.count == arr2.count, "Cannot diff arrays of different sizes");
        doDiff(0, arr1.shift, arr1.arr, arr2.arr, diffResultConsumer);
    }

    private static void doDiff(
        int currentIndex, int level, Object arr1, Object arr2, DiffResultConsumer diffResultConsumer)
    {
        if (arr1 == arr2)
        {
            return;
        }
        if (level != PRIM_SHIFT)
        {
            Object[] array1 = (Object[]) arr1;
            Object[] array2 = (Object[]) arr2;
            int count = array1.length;
            level -= countToShift(count);
            int subcnt = 1 << level;
            for (int i = 0; i < count; i++)
            {
                doDiff(currentIndex, level, array1[i], array2[i], diffResultConsumer);
                currentIndex += subcnt;
            }
        }
        else
        {
            byte[] array1 = (byte[]) arr1;
            byte[] array2 = (byte[]) arr2;
            for (int i = 0; i < (1 << PRIM_SHIFT); i++)
            {
                byte oldVal = array1[i];
                byte newVal = array2[i];
                if (oldVal != newVal)
                {
                    diffResultConsumer.different(currentIndex + i, oldVal, newVal);
                }
            }
        }
    }

    public static void write(PersistentByteArray arr, ByteBuf buf)
    {
        doWrite(arr.arr, arr.shift, buf);
    }

    private static void doWrite(Object arr, int level, ByteBuf buf)
    {
        if (level != PRIM_SHIFT)
        {
            Object[] array = (Object[]) arr;
            int count = array.length;
            level -= countToShift(count);
            for (Object subarr : array)
            {
                doWrite(subarr, level, buf);
            }
        }
        else
        {
            if (arr == EMPTY_PRIM_ARRAY)
            {
                buf.writeZero(1 << PRIM_SHIFT);
            }
            else
            {
                buf.writeBytes((byte[]) arr);
            }
        }
    }

    static final class TransientByteArray implements ITransientByteArray
    {
        private final AtomicBoolean editable = new AtomicBoolean(true);
        private final int count;
        private final int shift;
        private Object arr;

        TransientByteArray(int count, int shift, Object arr)
        {
            this.count = count;
            this.shift = shift;
            this.arr = arr;
        }

        @Override
        public int count()
        {
            ensureEditable();
            return count;
        }

        @Override
        public byte nthByte(int i)
        {
            ensureEditable();
            Objects.checkIndex(i, count);

            Object arr = this.arr;
            int level = shift;
            while (level != PRIM_SHIFT)
            {
                if (arr instanceof Node)
                {
                    arr = ((Node) arr).arr;
                }
                Object[] array = (Object[]) arr;
                int count = array.length;
                level -= countToShift(count);
                arr = array[(i >>> level) & (count - 1)];
            }
            if (arr instanceof Node)
            {
                arr = ((Node) arr).arr;
            }
            return ((byte[]) arr)[i & ((1 << PRIM_SHIFT) - 1)];
        }

        @Override
        public ITransientByteArray assocByte(int i, byte val)
        {
            ensureEditable();
            Objects.checkIndex(i, count);

            arr = doAssoc(arr, shift, i, val);
            return this;
        }

        private static Object doAssoc(Object arr, int level, int i, byte val)
        {
            Node node = ensureEditable(arr, level);
            arr = node.arr;
            if (level != PRIM_SHIFT)
            {
                Object[] array = (Object[]) arr;
                int count = array.length;
                level -= countToShift(count);
                int subidx = (i >>> level) & (count - 1);
                array[subidx] = doAssoc(array[subidx], level, i, val);
            }
            else
            {
                ((byte[]) arr)[i & ((1 << PRIM_SHIFT) - 1)] = val;
            }
            return node;
        }

        private static Node ensureEditable(Object arr, int level)
        {
            if (arr instanceof Node)
            {
                return (Node) arr;
            }
            return new Node(level != PRIM_SHIFT
                ? ((Object[]) arr).clone()
                : ((byte[]) arr).clone());
        }

        @Override
        public IPersistentByteArray persistent()
        {
            if (!editable.compareAndSet(true, false))
            {
                throw new IllegalAccessError("Transient used after persistent! call");
            }
            return new PersistentByteArray(count, shift, doPersistent(arr, shift));
        }

        private static Object doPersistent(Object arr, int level)
        {
            if (arr instanceof Node)
            {
                arr = ((Node) arr).arr;
                if (level != PRIM_SHIFT)
                {
                    Object[] array = (Object[]) arr;
                    int count = array.length;
                    level -= countToShift(count);
                    for (int i = 0; i < count; i++)
                    {
                        array[i] = doPersistent(array[i], level);
                    }
                }
            }
            return arr;
        }

        private void ensureEditable()
        {
            if (!editable.get())
            {
                throw new IllegalAccessError("Transient used after persistent! call");
            }
        }
    }

    static final class Node
    {
        final Object arr;

        Node(Object arr)
        {
            this.arr = arr;
        }
    }
}
