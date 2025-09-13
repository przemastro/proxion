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
            requestData.setLength(0);

            // Create new transaction
            currentTransaction = new HttpTransaction();
            currentTransaction.setMethod(request.method().name());
            currentTransaction.setUrl(request.uri());
            currentTransaction.setRequestHeaders(request.headers().toString());

            System.out.println("🌐 Intercepted HTTP " + request.method().name() + " " + request.uri());

            requestData.append("=== HTTP REQUEST ===\n");
            requestData.append("Method: ").append(request.method().name()).append("\n");
            requestData.append("URI: ").append(request.uri()).append("\n");
            requestData.append("Headers:\n").append(request.headers().toString()).append("\n");

            // Dodaj do GUI od razu
            if (mainController != null) {
                mainController.addHttpTransaction(currentTransaction);
            }
        }

        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;

            if (content.content().readableBytes() > 0) {
                String body = content.content().toString(CharsetUtil.UTF_8);
                requestData.append("Body: ").append(body).append("\n");
                if (currentTransaction != null) {
                    currentTransaction.setRequestBody(body);
                }
            }

            if (msg instanceof LastHttpContent) {
                requestData.append("=====================\n");
                System.out.println("📋 Request captured:\n" + requestData.toString());
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