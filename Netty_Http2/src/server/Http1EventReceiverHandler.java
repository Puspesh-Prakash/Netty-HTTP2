package server;



import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;

/**
 * @author Puspesh.Prakash
 * 
 * HTTP handler that responds with a Event ACK.
 */
public class Http1EventReceiverHandler extends SimpleChannelInboundHandler<FullHttpRequest> 
{
    private final String establishApproach;

    public Http1EventReceiverHandler(String establishApproach) 
    {
        this.establishApproach = checkNotNull(establishApproach, "establishApproach");
    }

    /**
	 * Reads the channel for incoming data from client(s).
	 */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception 
    {
        if (HttpUtil.is100ContinueExpected(req)) 
        {
            ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER));
        }
           
        boolean keepAlive = HttpUtil.isKeepAlive(req);
        
        String responseJson = "{\"response-code\":\"202\",\"response-message\":\"Accepted\"}";

        ByteBuf content = ctx.alloc().buffer();     
        
        ByteBuf responseBytes = wrappedBuffer(responseJson.getBytes());
        
        content.writeBytes(responseBytes.duplicate()); 
        
        ByteBufUtil.writeAscii(content, " - via " + req.protocolVersion() + " (" + establishApproach + ")");

        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, responseBytes);
        
        response.headers().set(CONTENT_TYPE, "application/json");
        
        response.headers().setInt(CONTENT_LENGTH, response.content().readableBytes());

        if (keepAlive) 
        {
            if (req.protocolVersion().equals(HTTP_1_0)) 
            {
                response.headers().set(CONNECTION, KEEP_ALIVE);
            }
            
            ctx.write(response);
        } 
        
        else 
        {
            // Tells the client that it is going to close the connection.
            response.headers().set(CONNECTION, CLOSE);
            
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
	 * Flushes the channel when it is fired for accepting new requests.
	 */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception 
    {
        ctx.flush();
    }

    /**
	 * Handles exceptions caught while processing incoming data from client.
	 */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
    {
        cause.printStackTrace();
        
        ctx.close();
    }
}