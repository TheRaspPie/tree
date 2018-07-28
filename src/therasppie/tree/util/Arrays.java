package therasppie.tree.util;

public final class Arrays
{
    public static <T> T[] assocO(T[] array, int i, T v)
    {
        T[] newArray = array.clone();
        newArray[i] = v;
        return newArray;
    }

    public static char[] assocC(char[] array, int i, char v)
    {
        char[] newArray = array.clone();
        newArray[i] = v;
        return newArray;
    }

    public static <T> void diffO(int length, T[] array1, T[] array2, ODiffConsumer<T> consumer)
    {
        for (int i = length - 1; i >= 0; i--)
        {
            T v1 = array1[i], v2 = array2[i];
            if (v1 != v2)
            {
                consumer.different(i, v1, v2);
            }
        }
    }

    public static void diffC(int length, char[] array1, char[] array2, CDiffConsumer consumer)
    {
        for (int i = length - 1; i >= 0; i--)
        {
            char v1 = array1[i], v2 = array2[i];
            if (v1 != v2)
            {
                consumer.different(i, v1, v2);
            }
        }
    }

    public interface ODiffConsumer<T>
    {
        void different(int i, T oldValue, T newValue);
    }

    public interface CDiffConsumer
    {
        void different(int i, char oldValue, char newValue);
    }
}
