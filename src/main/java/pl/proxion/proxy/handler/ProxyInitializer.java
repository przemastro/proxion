package pl.proxion.proxy.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import pl.proxion.controller.MainController;

public class ProxyInitializer extends ChannelInitializer<SocketChannel> {

    private MainController mainController;

    public ProxyInitializer(MainController controller) {
        this.mainController = controller;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        System.out.println("🔌 New connection from: " + ch.remoteAddress());

        ch.pipeline().addLast(
                new LoggingHandler(LogLevel.INFO),
                new HttpServerCodec(),
                new HttpTrafficHandler(mainController), // Dodaj handler do przechwytywania ruchu
                new ProxyFrontendHandler(mainController)
        );

        System.out.println("✅ Pipeline setup complete");
    }
}