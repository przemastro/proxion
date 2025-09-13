package pl.proxion.proxy.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import pl.proxion.controller.MainController;
import pl.proxion.model.HttpTransaction;

public class RealServerHandler extends ChannelInboundHandlerAdapter {

    private final Channel clientChannel;
    private final MainController mainController;
    private final HttpTransaction transaction;

    public RealServerHandler(Channel clientChannel, MainController mainController, HttpTransaction transaction) {
        this.clientChannel = clientChannel;
        this.mainController = mainController;
        this.transaction = transaction;
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;

            int originalStatusCode = response.status().code();
            int finalStatusCode = originalStatusCode;
            boolean wasModified = false;

            // Zastosuj reguły rewrite jeśli mamy dostęp do kontrolera i URL
            if (mainController != null && transaction != null && transaction.getUrl() != null) {
                int newStatusCode = mainController.applyStatusCodeRewrite(originalStatusCode, transaction.getUrl());
                if (newStatusCode != originalStatusCode) {
                    response = createModifiedResponse(response, newStatusCode);
                    finalStatusCode = newStatusCode;
                    wasModified = true;
                    System.out.println("🔄 Rewrote status code: " + originalStatusCode + " → " + newStatusCode);
                }
            }

            // Uzupełnij transakcję o odpowiedź
            if (transaction != null) {
                transaction.setStatusCode(finalStatusCode);
                transaction.setOriginalStatusCode(originalStatusCode);
                transaction.setModified(wasModified);
                transaction.setResponseHeaders(response.headers().toString());

                if (response.content().readableBytes() > 0) {
                    try {
                        String responseBody = response.content().toString(CharsetUtil.UTF_8);
                        transaction.setResponseBody(responseBody);
                        System.out.println("📄 Response body: " + responseBody.length() + " characters");
                    } catch (Exception e) {
                        System.out.println("🔒 Encrypted response detected, storing as binary data");
                        transaction.setResponseBody("[ENCRYPTED CONTENT - " + response.content().readableBytes() + " bytes]");
                    }
                } else {
                    transaction.setResponseBody("[EMPTY RESPONSE]");
                }

                // Odśwież w kontrolerze
                if (mainController != null) {
                    mainController.addHttpTransaction(transaction);
                }
            }

            System.out.println("📨 Response from server: " + originalStatusCode +
                    (wasModified ? " → " + finalStatusCode + " (MODIFIED)" : ""));

            // Przekaż odpowiedź do klienta
            clientChannel.writeAndFlush(response.retain()).addListener(new ChannelFutureListener() {
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
    }

    private FullHttpResponse createModifiedResponse(FullHttpResponse originalResponse, int newStatusCode) {
        // Tworzymy nową odpowiedź z zmienionym status code
        FullHttpResponse modifiedResponse = new DefaultFullHttpResponse(
                originalResponse.protocolVersion(),
                HttpResponseStatus.valueOf(newStatusCode),
                originalResponse.content().copy(),
                originalResponse.headers().copy(),
                originalResponse.trailingHeaders().copy()
        );

        // Aktualizuj nagłówek Content-Length jeśli potrzeba
        HttpUtil.setContentLength(modifiedResponse, modifiedResponse.content().readableBytes());

        return modifiedResponse;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("🔌 Connection to real server closed");
        clientChannel.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println("❌ Error in RealServerHandler: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
        clientChannel.close();
    }
}