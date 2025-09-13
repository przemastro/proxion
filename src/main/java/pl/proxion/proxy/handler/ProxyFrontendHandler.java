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
    private ChannelHandlerContext clientContext;

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
        // Tworzymy transakcję dla połączenia HTTPS
        HttpTransaction transaction = new HttpTransaction();
        transaction.setMethod("CONNECT");
        transaction.setUrl(request.uri());
        transaction.setRequestHeaders(request.headers().toString());
        transaction.setStatusCode(200);
        transaction.setResponseHeaders("HTTP/1.1 200 Connection Established");
        transaction.setResponseBody("HTTPS tunnel established");

        // Dodaj do kontrolera
        if (mainController != null) {
            mainController.addHttpTransaction(transaction);
        }

        // Odpowiadamy 200 OK na CONNECT
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK
        );
        ctx.writeAndFlush(response);

        // Usuwamy HTTP codec i traffic handler po klasie, nie po nazwie
        ctx.pipeline().remove(HttpServerCodec.class);
        ctx.pipeline().remove(HttpTrafficHandler.class);

        // Rozdzielamy host i port z URI (format: host:port)
        String[] parts = request.uri().split(":");
        final String host = parts[0];
        final int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 443;

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
                    System.out.println("✅ HTTPS tunnel established to " + host + ":" + port);
                } else {
                    System.err.println("❌ Failed to establish HTTPS tunnel: " + future.cause().getMessage());
                    ctx.close();
                }
            }
        });
    }

    private void connectToRealServer(ChannelHandlerContext ctx, HttpRequest request) {
        this.clientContext = ctx;

        try {
            String originalUri = request.uri();

            // Deklarujemy zmienne z domyślnymi wartościami
            String targetHost = "localhost";
            int targetPort = 80;
            boolean isHttps = false;

            // Parsuj URL
            if (originalUri.startsWith("http://")) {
                URI uri = new URI(originalUri);
                targetHost = uri.getHost();
                targetPort = uri.getPort() > 0 ? uri.getPort() : 80;
                isHttps = false;
            } else if (originalUri.startsWith("https://")) {
                URI uri = new URI(originalUri);
                targetHost = uri.getHost();
                targetPort = uri.getPort() > 0 ? uri.getPort() : 443;
                isHttps = true;
            } else {
                // Dla względnych URL, używamy hosta z nagłówka
                String hostHeader = request.headers().get(HttpHeaderNames.HOST);
                if (hostHeader != null) {
                    // Usuń port jeśli istnieje
                    if (hostHeader.contains(":")) {
                        String[] parts = hostHeader.split(":");
                        targetHost = parts[0];
                        targetPort = Integer.parseInt(parts[1]);
                    } else {
                        targetHost = hostHeader;
                        targetPort = 80;
                    }
                }
                // Jeśli nie ma nagłówka HOST, zostawiamy domyślne wartości
                isHttps = false;
            }

            // Teraz deklarujemy jako final dla klas wewnętrznych
            final String finalTargetHost = targetHost;
            final int finalTargetPort = targetPort;
            final boolean finalIsHttps = isHttps;

            System.out.println("🌐 Connecting to real server: " + finalTargetHost + ":" + finalTargetPort + " (HTTPS: " + finalIsHttps + ")");

            // Tworzymy transakcję
            final HttpTransaction transaction = new HttpTransaction();
            transaction.setMethod(request.method().name());
            transaction.setUrl(originalUri);
            transaction.setRequestHeaders(request.headers().toString());

            if (mainController != null) {
                mainController.addHttpTransaction(transaction);
                currentTransaction = transaction;
            }

            // Łączymy się z rzeczywistym serwerem
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(ctx.channel().eventLoop())
                    .channel(ctx.channel().getClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            if (finalIsHttps) {
                                ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), finalTargetHost, finalTargetPort));
                            }
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new RealServerHandler(ctx.channel(), mainController, transaction));
                        }
                    });

            ChannelFuture future = bootstrap.connect(finalTargetHost, finalTargetPort);
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        backendChannel = future.channel();
                        System.out.println("✅ Connected to real server: " + finalTargetHost + ":" + finalTargetPort);

                        // Przekaż oryginalne żądanie do serwera
                        backendChannel.writeAndFlush(request);
                    } else {
                        System.err.println("❌ Failed to connect to real server: " + future.cause().getMessage());

                        // Zwróć błąd do klienta
                        DefaultFullHttpResponse errorResponse = new DefaultFullHttpResponse(
                                HttpVersion.HTTP_1_1,
                                HttpResponseStatus.BAD_GATEWAY,
                                Unpooled.copiedBuffer("Failed to connect to server: " + future.cause().getMessage(), CharsetUtil.UTF_8)
                        );
                        ctx.writeAndFlush(errorResponse).addListener(ChannelFutureListener.CLOSE);
                    }
                }
            });

        } catch (Exception e) {
            System.err.println("❌ Error connecting to real server: " + e.getMessage());

            DefaultFullHttpResponse errorResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Unpooled.copiedBuffer("Proxy error: " + e.getMessage(), CharsetUtil.UTF_8)
            );
            ctx.writeAndFlush(errorResponse).addListener(ChannelFutureListener.CLOSE);
        }
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
                connectToRealServer(ctx, request);
            }
        } else if (backendChannel != null && backendChannel.isActive()) {
            // Przekaż inne wiadomości (np. HttpContent) do backendu
            backendChannel.writeAndFlush(msg);
        }
    }
}