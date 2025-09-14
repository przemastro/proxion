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
    private boolean isHttpsTunnel = false;

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
        System.out.println("🔐 Handling HTTPS CONNECT: " + request.uri());

        // Tworzymy transakcję dla połączenia HTTPS
        HttpTransaction transaction = new HttpTransaction();
        transaction.setMethod("CONNECT");
        transaction.setUrl(request.uri());
        transaction.setRequestHeaders(request.headers().toString());
        transaction.setStatusCode(200);
        transaction.setResponseHeaders("HTTP/1.1 200 Connection Established");
        transaction.setResponseBody("HTTPS tunnel established");
        transaction.setEncrypted(true);

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

        // Usuwamy handlery HTTP, bo teraz będziemy przekazywać surowe bajty
        ChannelPipeline pipeline = ctx.pipeline();
        if (pipeline.get(HttpServerCodec.class) != null) {
            pipeline.remove(HttpServerCodec.class);
        }
        if (pipeline.get(HttpTrafficHandler.class) != null) {
            pipeline.remove(HttpTrafficHandler.class);
        }
        if (pipeline.get(ProxyFrontendHandler.class) != null) {
            pipeline.remove(ProxyFrontendHandler.class);
        }

        // Rozdzielamy host i port z URI (format: host:port)
        String[] parts = request.uri().split(":");
        final String host = parts[0];
        final int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 443;

        System.out.println("🔗 Establishing HTTPS tunnel to: " + host + ":" + port);

        // Oznaczamy, że to tunel HTTPS
        isHttpsTunnel = true;

        // Łączymy się z docelowym serwerem
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // Dodajemy SSL dla połączenia do serwera
                        ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), host, port));
                        // Handler do przekazywania danych przez tunel
                        ch.pipeline().addLast(new TunnelingBackendHandler(ctx.channel()));
                    }
                });

        ChannelFuture connectFuture = bootstrap.connect(host, port);
        connectFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    backendChannel = future.channel();
                    System.out.println("✅ HTTPS tunnel established to " + host + ":" + port);

                    // Dodajemy handler do przekazywania danych od klienta do serwera
                    ctx.pipeline().addLast(new TunnelingFrontendHandler(backendChannel));

                    // Ustawiamy wzajemne przekazywanie danych
                    ctx.channel().config().setAutoRead(true);
                    backendChannel.config().setAutoRead(true);
                } else {
                    System.err.println("❌ Failed to establish HTTPS tunnel: " + future.cause().getMessage());
                    DefaultFullHttpResponse errorResponse = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.BAD_GATEWAY,
                            Unpooled.copiedBuffer("Failed to establish HTTPS tunnel: " +
                                    future.cause().getMessage(), CharsetUtil.UTF_8)
                    );
                    ctx.writeAndFlush(errorResponse).addListener(ChannelFutureListener.CLOSE);
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
                    if (hostHeader.contains(":")) {
                        String[] parts = hostHeader.split(":");
                        targetHost = parts[0];
                        targetPort = Integer.parseInt(parts[1]);
                    } else {
                        targetHost = hostHeader;
                        targetPort = 80;
                    }
                }
                isHttps = false;
            }

            // Finalne zmienne dla klas wewnętrznych
            final String finalTargetHost = targetHost;
            final int finalTargetPort = targetPort;
            final boolean finalIsHttps = isHttps;

            System.out.println("🌐 Connecting to server: " + finalTargetHost + ":" + finalTargetPort +
                    " (HTTPS: " + finalIsHttps + ")");

            // Tworzymy transakcję
            final HttpTransaction transaction = new HttpTransaction();
            transaction.setMethod(request.method().name());
            transaction.setUrl(originalUri);
            transaction.setRequestHeaders(request.headers().toString());
            transaction.setEncrypted(finalIsHttps);

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
                                // Dodajemy SSL handler dla HTTPS
                                ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), finalTargetHost, finalTargetPort));
                            }
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(10485760)); // 10MB limit
                            ch.pipeline().addLast(new RealServerHandler(ctx.channel(), mainController, transaction));
                        }
                    });

            ChannelFuture future = bootstrap.connect(finalTargetHost, finalTargetPort);
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        backendChannel = future.channel();
                        System.out.println("✅ Connected to server: " + finalTargetHost + ":" + finalTargetPort);

                        // Przekaż oryginalne żądanie do serwera
                        if (request instanceof FullHttpRequest) {
                            backendChannel.writeAndFlush(((FullHttpRequest) request).retain());
                        } else {
                            backendChannel.writeAndFlush(request);
                        }
                    } else {
                        System.err.println("❌ Failed to connect to server: " + future.cause().getMessage());

                        // Uaktualnij transakcję o błąd
                        if (transaction != null) {
                            transaction.setStatusCode(502);
                            transaction.setResponseBody("Failed to connect to server: " +
                                    future.cause().getMessage());
                            if (mainController != null) {
                                mainController.addHttpTransaction(transaction);
                            }
                        }

                        // Zwróć błąd do klienta
                        DefaultFullHttpResponse errorResponse = new DefaultFullHttpResponse(
                                HttpVersion.HTTP_1_1,
                                HttpResponseStatus.BAD_GATEWAY,
                                Unpooled.copiedBuffer("Failed to connect to server: " +
                                        future.cause().getMessage(), CharsetUtil.UTF_8)
                        );
                        ctx.writeAndFlush(errorResponse).addListener(ChannelFutureListener.CLOSE);
                    }
                }
            });

        } catch (Exception e) {
            System.err.println("❌ Error connecting to server: " + e.getMessage());

            // Uaktualnij transakcję o błąd
            if (currentTransaction != null) {
                currentTransaction.setStatusCode(500);
                currentTransaction.setResponseBody("Proxy error: " + e.getMessage());
                if (mainController != null) {
                    mainController.addHttpTransaction(currentTransaction);
                }
            }

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

        // Uaktualnij transakcję o błąd
        if (currentTransaction != null) {
            currentTransaction.setStatusCode(500);
            currentTransaction.setResponseBody("Proxy handler error: " + cause.getMessage());
            if (mainController != null) {
                mainController.addHttpTransaction(currentTransaction);
            }
        }

        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("🔌 Client connection closed");
        if (backendChannel != null && backendChannel.isActive()) {
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
        // Dla tunelu HTTPS, po prostu przekazujemy dane dalej
        if (isHttpsTunnel) {
            if (backendChannel != null && backendChannel.isActive()) {
                backendChannel.writeAndFlush(msg);
            }
            return;
        }

        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            System.out.println("📨 HTTP " + request.method() + " " + request.uri());

            if (request.method() == HttpMethod.CONNECT) {
                System.out.println("🔐 HTTPS CONNECT request: " + request.uri());
                handleHttpsConnection(ctx, request);
            } else {
                System.out.println("🌐 HTTP request: " + request.uri());
                connectToRealServer(ctx, request);
            }
        } else if (backendChannel != null && backendChannel.isActive()) {
            // Przekaż inne wiadomości (np. HttpContent) do backendu
            backendChannel.writeAndFlush(msg);
        } else if (msg instanceof HttpContent) {
            // Jeśli nie ma aktywnego backendChannel, zwolnij zasoby
            ((HttpContent) msg).release();
        }
    }

    // Handler dla tunelowania HTTPS - przekazywanie od klienta do serwera
    private static class TunnelingFrontendHandler extends ChannelInboundHandlerAdapter {
        private final Channel backendChannel;

        public TunnelingFrontendHandler(Channel backendChannel) {
            this.backendChannel = backendChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // Przekazujemy dane z frontendu do backendu
            backendChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ctx.read();
                    } else {
                        future.channel().close();
                    }
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("🔌 HTTPS tunnel frontend connection closed");
            if (backendChannel != null && backendChannel.isActive()) {
                backendChannel.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.err.println("❌ Error in HTTPS tunnel frontend: " + cause.getMessage());
            cause.printStackTrace();
            if (backendChannel != null && backendChannel.isActive()) {
                backendChannel.close();
            }
            ctx.close();
        }
    }

    // Handler dla tunelowania HTTPS - przekazywanie od serwera do klienta
    private static class TunnelingBackendHandler extends ChannelInboundHandlerAdapter {
        private final Channel frontendChannel;

        public TunnelingBackendHandler(Channel frontendChannel) {
            this.frontendChannel = frontendChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // Przekazujemy dane z backendu do frontendu
            frontendChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ctx.read();
                    } else {
                        future.channel().close();
                    }
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("🔌 HTTPS tunnel backend connection closed");
            frontendChannel.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.err.println("❌ Error in HTTPS tunnel backend: " + cause.getMessage());
            cause.printStackTrace();
            frontendChannel.close();
            ctx.close();
        }
    }
}