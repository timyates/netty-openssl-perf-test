package ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.stream.ChunkedNioFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.util.CharsetUtil.UTF_8;

final class Handler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private Path resourceFile;
    private long fileLength;

    Handler() throws Exception {
        resourceFile = Paths.get(Main.class.getResource("/file").toURI());
        fileLength = Files.size(resourceFile);
    }

    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", UTF_8));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String path = request.uri();
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        switch (path) {
            case "/empty":
                ok(ctx, keepAlive);
                break;
            case "/ping":
                pong(ctx, keepAlive);
                break;
            case "/file":
                file(ctx, keepAlive);
                break;
            default:
                sendError(ctx, NOT_FOUND);
        }
    }

    private void ok(ChannelHandlerContext ctx, boolean keepAlive) {
        text(ctx, Unpooled.EMPTY_BUFFER, keepAlive);
    }

    private void pong(ChannelHandlerContext ctx, boolean keepAlive) {
        text(ctx, Unpooled.wrappedBuffer("pong".getBytes(UTF_8)), keepAlive);
    }

    private void text(ChannelHandlerContext ctx, ByteBuf data, boolean keepAlive) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, data);
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(CONTENT_LENGTH, response.content().readableBytes());
        finish(ctx.writeAndFlush(response), keepAlive);
    }

    private void file(ChannelHandlerContext ctx, boolean keepAlive) throws IOException {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        HttpUtil.setContentLength(response, fileLength);
        response.headers().set(CONTENT_TYPE, "application/octet-stream");
        ctx.write(response);
        finish(ctx.writeAndFlush(new HttpChunkedInput(new ChunkedNioFile(resourceFile.toFile())), ctx.newProgressivePromise()), keepAlive);
    }

    private void finish(ChannelFuture f, boolean keepAlive) {
        if (!keepAlive) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

}
