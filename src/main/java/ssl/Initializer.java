package ssl;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;

final class Initializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslContext;

    Initializer(SslContext sslContext) {
        this.sslContext = sslContext;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.config().setAllocator(PooledByteBufAllocator.DEFAULT);
        ch.pipeline()
                .addLast(sslContext.newHandler(ch.alloc()))
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(65536))
                .addLast(new ChunkedWriteHandler())
                .addLast(new Handler());
    }

}
