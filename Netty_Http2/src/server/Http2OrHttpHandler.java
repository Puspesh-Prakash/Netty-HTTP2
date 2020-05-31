package server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;

/**
 * @author Puspesh.Prakash
 * 
 * Negotiates with the browser if HTTP2 or HTTP is going to be used. Once decided, the Netty
 * pipeline is setup with the correct handlers for the selected protocol.
 */
public class Http2OrHttpHandler extends ApplicationProtocolNegotiationHandler {

    private static final int MAX_CONTENT_LENGTH = 1024 * 1024; //1 MB

    protected Http2OrHttpHandler() 
    {
    	super(ApplicationProtocolNames.HTTP_2);
    	
//        super(ApplicationProtocolNames.HTTP_1_1);
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception 
    {
        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) 
        {
            ctx.pipeline().addLast(Http2FrameCodecBuilder.forServer().build());
            ctx.pipeline().addLast(new Http2MultiplexHandler(new Http2EventReceiverHandler()));
            return;
        }

        if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) 
        {
            ctx.pipeline().addLast(new HttpServerCodec(),
                                   new HttpObjectAggregator(MAX_CONTENT_LENGTH),
                                   new Http1EventReceiverHandler("ALPN Negotiation"));
            return;
        }

        throw new IllegalStateException("Unknown Protocol: " + protocol);
    }
}
