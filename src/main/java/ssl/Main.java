package ssl;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.util.Collections;

@SuppressWarnings("Duplicates")
final class Main {

    private final CliArgs args = new CliArgs();


    public static void main(String... args) throws Exception {
        new Main().start(args);
    }

    private void start(String[] cliArgs) throws Exception {
        parseCliArgs(cliArgs);
        if (Epoll.isAvailable()) {
            runServer(new EpollEventLoopGroup(1), new EpollEventLoopGroup(), EpollServerSocketChannel.class, sslContext());
        } else {
            runServer(new NioEventLoopGroup(1), new NioEventLoopGroup(), NioServerSocketChannel.class, sslContext());
        }

    }

    private void runServer(EventLoopGroup boss, EventLoopGroup elg, Class<? extends ServerChannel> serverChannelClass, SslContext sslContext) throws Exception {
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, elg)
                    .channel(serverChannelClass)
                    .childHandler(new Initializer(sslContext))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // Start the server.
            ChannelFuture f = b.bind(8080).sync();
            System.err.println("Open your web browser and navigate to https://127.0.0.1:8080/");
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            elg.shutdownGracefully();
            boss.shutdownGracefully();
        }
    }

    private SslContext sslContext() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        SslContextBuilder sslContextBuilder = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey());

        Throwable unavailabilityCause = OpenSsl.unavailabilityCause();

        if (args.javaSsl || (unavailabilityCause != null)) {
            System.out.println("Using JDK SSL");
            sslContextBuilder
                    .ciphers(Collections.singletonList("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"))
                    .sslProvider(SslProvider.JDK);
        } else {
            System.out.println("Using OpenSSL");

            boolean ocspSupported = OpenSsl.isOcspSupported();

            if (ocspSupported) {
                System.out.println("OCSP Stapling enabled");
            }

            sslContextBuilder
                    .ciphers(Collections.singletonList("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"))
                    .enableOcsp(ocspSupported)
                    .sslProvider(SslProvider.OPENSSL_REFCNT);
        }

        return sslContextBuilder.build();
    }

    private void parseCliArgs(String[] rawCliArgs) {
        JCommander jCommander = new JCommander(args);
        jCommander.setProgramName("netty");
        jCommander.parse(rawCliArgs);
    }

    private static final class CliArgs {

        @Parameter(names = {"--java-ssl"}, description = "Use the JDK SSL")
        boolean javaSsl;

    }

}
