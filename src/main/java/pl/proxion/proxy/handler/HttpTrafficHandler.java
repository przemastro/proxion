package pl.proxion.proxy.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import pl.proxion.controller.MainController;
import pl.proxion.model.HttpTransaction;

public class HttpTrafficHandler extends ChannelInboundHandlerAdapter {

    private StringBuilder requestData = new StringBuilder();
    private HttpTransaction currentTransaction;
    private MainController mainController;

    public HttpTrafficHandler(MainController controller) {
        this.mainController = controller;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;

            // Create new transaction
            currentTransaction = new HttpTransaction();
            currentTransaction.setMethod(request.method().name());
            currentTransaction.setUrl(request.uri());
            currentTransaction.setRequestHeaders(request.headers().toString());

            System.out.println("🌐 Intercepted HTTP " + request.method().name() + " " + request.uri());
        }

        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;
            if (content.content().readableBytes() > 0 && currentTransaction != null) {
                try {
                    String requestBody = content.content().toString(CharsetUtil.UTF_8);
                    currentTransaction.setRequestBody(requestBody);
                } catch (Exception e) {
                    currentTransaction.setRequestBody("[BINARY DATA - " + content.content().readableBytes() + " bytes]");
                }
            }

            if (msg instanceof LastHttpContent) {
                if (currentTransaction != null && mainController != null) {
                    mainController.addHttpTransaction(currentTransaction);
                    currentTransaction = null;
                }
            }
        }

        // Przekazujemy dalej
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("❌ Error in HttpTrafficHandler: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}