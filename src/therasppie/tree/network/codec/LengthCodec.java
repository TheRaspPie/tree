package therasppie.tree.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

public class LengthCodec extends ByteToMessageCodec<ByteBuf>
{
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception
    {
        VarIntUtil.writeVarInt(out, msg.readableBytes());
        out.writeBytes(msg);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception
    {
        int readerIndex = in.readerIndex();
        int length = VarIntUtil.readVarIntSoft(in);
        if (readerIndex == in.readerIndex())
        {
            return;
        }

        if (length < 0)
        {
            throw new CorruptedFrameException("Negative length: " + length);
        }

        if (in.readableBytes() < length)
        {
            in.readerIndex(readerIndex);
            return;
        }

        out.add(in.readRetainedSlice(length));
    }
}
