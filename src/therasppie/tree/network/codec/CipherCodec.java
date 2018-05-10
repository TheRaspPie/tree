package therasppie.tree.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import javax.crypto.Cipher;
import java.util.List;

public class CipherCodec extends MessageToMessageCodec<ByteBuf, ByteBuf>
{
    private final Cipher encrypt;
    private final Cipher decrypt;

    public CipherCodec(Cipher encrypt, Cipher decrypt)
    {
        this.encrypt = encrypt;
        this.decrypt = decrypt;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception
    {
        out.add(CipherUtil.cipher(msg, encrypt, ctx.alloc()));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception
    {
        out.add(CipherUtil.cipher(msg, decrypt, ctx.alloc()));
    }
}
