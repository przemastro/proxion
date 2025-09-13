package pl.proxion.proxy.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
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

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;

            // Uzupełnij transakcję o odpowiedź
            if (transaction != null) {
                transaction.setStatusCode(response.status().code());
                transaction.setResponseHeaders(response.headers().toString());

                if (response.content().readableBytes() > 0) {
                    String responseBody = response.content().toString(CharsetUtil.UTF_8);
                    transaction.setResponseBody(responseBody);
                }

                // Odśwież w kontrolerze jeśli potrzebne
                if (mainController != null) {
                    mainController.addHttpTransaction(transaction);
                }
            }

            System.out.println("📨 Response from server: " + response.status().code() + " " + response.status().reasonPhrase());

            // Przekaż odpowiedź do klienta
            clientChannel.writeAndFlush(response).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ctx.read();
                    } else {
                        ctx.close();
                    }
                }
            });
        }
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