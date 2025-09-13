package pl.proxion.proxy.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import pl.proxion.controller.MainController;

public class ProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private SslContext sslContext;
    private MainController mainController;

    public ProxyFrontendHandler(MainController controller) {
        this.mainController = controller;
        try {
            this.sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
        } catch (Exception e) {
            System.err.println("❌ Error creating SSL context: " + e.getMessage());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;

            System.out.println("📨 Received request: " + request.method().name() + " " + request.uri());

            if (request.method() == HttpMethod.CONNECT) {
                // To jest połączenie HTTPS
                System.out.println("🔐 HTTPS connection detected");
                handleHttpsConnection(ctx, request);
            } else {
                // To jest zwykłe HTTP
                System.out.println("🌐 HTTP connection detected");
                handleHttpConnection(ctx, request, msg);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleHttpsConnection(ChannelHandlerContext ctx, HttpRequest request) {
        // Tutaj będzie obsługa HTTPS (CONNECT)
        System.out.println("⚠️ HTTPS handling not implemented yet for: " + request.uri());
        ctx.close();
    }

    private void handleHttpConnection(ChannelHandlerContext ctx, HttpRequest request, Object msg) {
        // Usuwamy SSL handler dla HTTP
        try {
            ctx.pipeline().remove(io.netty.handler.ssl.SslHandler.class);
        } catch (Exception e) {
            // SSL handler may not exist, which is fine
        }

        // Dodajemy handler dla HTTP traffic
        ctx.pipeline().addAfter("httpServerCodec", "httpTrafficHandler", new HttpTrafficHandler(mainController));

        // Przekazujemy wiadomość dalej
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("❌ Error in ProxyFrontendHandler: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}