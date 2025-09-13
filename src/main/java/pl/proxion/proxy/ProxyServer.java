package pl.proxion.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import pl.proxion.controller.MainController;
import pl.proxion.proxy.handler.ProxyInitializer;

public class ProxyServer {
    private final int port;
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private MainController mainController;

    public ProxyServer(int port, MainController controller) {
        this.port = port;
        this.mainController = controller;
    }

    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            System.out.println("🚀 Starting server bootstrap...");
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ProxyInitializer(mainController))
                    .childOption(ChannelOption.AUTO_READ, true);

            System.out.println("📡 Binding to port " + port + "...");
            serverChannel = b.bind(port).sync().channel();
            System.out.println("✅ Proxion proxy started successfully on port " + port);
            System.out.println("🌐 Ready for HTTP traffic");

            serverChannel.closeFuture().sync();

        } catch (Exception e) {
            System.err.println("❌ Error starting proxy server: " + e.getMessage());
            throw e;
        } finally {
            shutdown();
        }
    }

    public void stop() {
        System.out.println("🛑 Stopping proxy server...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        shutdown();
        System.out.println("✅ Proxy server stopped");
    }

    private void shutdown() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}