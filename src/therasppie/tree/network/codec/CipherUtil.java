package therasppie.tree.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.concurrent.FastThreadLocal;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;

public class CipherUtil
{
    private static final FastThreadLocal<byte[]> INPUT = new FastThreadLocal<>()
    {
        @Override
        protected byte[] initialValue()
        {
            return new byte[16];
        }
    };

    public static ByteBuf cipher(ByteBuf msg, Cipher cipher, ByteBufAllocator alloc) throws ShortBufferException
    {
        int inputLength = msg.readableBytes();
        byte[] input = INPUT.get();
        if (input.length < inputLength)
        {
            input = new byte[inputLength];
            INPUT.set(input);
        }

        msg.readBytes(input, 0, inputLength);

        int outputLength = cipher.getOutputSize(inputLength);

        ByteBuf buf = alloc.heapBuffer(outputLength, outputLength);
        cipher.update(input, 0, inputLength, buf.array(), buf.arrayOffset());
        buf.writerIndex(outputLength);

        return buf;
    }
}
