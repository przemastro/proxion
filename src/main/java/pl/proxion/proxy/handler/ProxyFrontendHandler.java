package pl.proxion.proxy.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import pl.proxion.controller.MainController;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import pl.proxion.model.HttpTransaction;

import java.net.URI;
import java.net.URISyntaxException;

public class ProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private SslContext sslContext;
    private MainController mainController;
    private Channel backendChannel;
    private HttpTransaction currentTransaction;

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

    private void handleHttpsConnection(ChannelHandlerContext ctx, HttpRequest request) {
        // Odpowiadamy 200 OK na CONNECT
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK
        );
        ctx.writeAndFlush(response);

        // Usuwamy HTTP codec i dodajemy tunneling
        ctx.pipeline().remove("httpServerCodec");
        ctx.pipeline().remove("httpTrafficHandler");

        // Rozdzielamy host i port z URI (format: host:port)
        String[] parts = request.uri().split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 443;

        System.out.println("🔗 Connecting to HTTPS backend: " + host + ":" + port);

        // Łączymy się z docelowym serwerem
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // Dodajemy SSL dla połączenia do serwera
                        ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), host, port));
                        ch.pipeline().addLast(new HttpBackendHandler(ctx.channel()));
                    }
                });

        bootstrap.connect(host, port).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    backendChannel = future.channel();
                    System.out.println("✅ HTTPS tunnel established");
                } else {
                    System.err.println("❌ Failed to establish HTTPS tunnel: " + future.cause().getMessage());
                    ctx.close();
                }
            }
        });
    }

    private void createAndSendTransaction(ChannelHandlerContext ctx, HttpRequest request, String responseBody, int statusCode) {
        if (mainController != null) {
            HttpTransaction transaction = new HttpTransaction();
            transaction.setMethod(request.method().name());
            transaction.setUrl(request.uri());
            transaction.setRequestHeaders(request.headers().toString());
            transaction.setResponseHeaders("Content-Type: application/json");
            transaction.setResponseBody(responseBody);
            transaction.setStatusCode(statusCode);

            mainController.addHttpTransaction(transaction);
        }
    }

    private void handleHttpConnection(ChannelHandlerContext ctx, HttpRequest request, Object msg) {
        String originalUri = request.uri();
        System.out.println("🌐 Original URI: " + originalUri);

        // Tymczasowa odpowiedź
        String responseBody = "{\"ip\": \"127.0.0.1\", \"proxy\": \"working\", \"path\": \"" + originalUri + "\"}";

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(responseBody, CharsetUtil.UTF_8)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, "close");

        // Dodaj transakcję do kontrolera
        createAndSendTransaction(ctx, request, responseBody, 200);

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("❌ Error in ProxyFrontendHandler: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (backendChannel != null) {
            backendChannel.close();
        }
        super.channelInactive(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("✅ Client connected: " + ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("📨 Received: " + msg.getClass().getSimpleName());

        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            System.out.println("🌐 HTTP " + request.method() + " " + request.uri());

            if (request.method() == HttpMethod.CONNECT) {
                System.out.println("🔐 HTTPS CONNECT: " + request.uri());
                handleHttpsConnection(ctx, request);
            } else {
                System.out.println("🌐 HTTP: " + request.uri());
                handleHttpConnection(ctx, request, msg);
            }
            return;
        }

        ctx.fireChannelRead(msg);
    }
}