package server;


import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.ACCEPTED;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

/**
 * @author Puspesh.Prakash
 * 
 * <p> This class handles requests received in dedicated http2 netty channel.
 * 
 * @Sharable : To share this channel handler with multiple clients/connections.
 */
@Sharable
public class Http2EventReceiverHandler extends ChannelDuplexHandler 
{	
	private Map <String, StringBuilder> requestMap = new ConcurrentHashMap<>(); 
	
	private Map <String, Http2Headers> headerMap = new ConcurrentHashMap<>(); 
	
	/**
	 * Handles exceptions caught while processing incoming data from client.
	 */
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception 
	{
		cause.printStackTrace();
		
		ctx.close();
	}

	/**
	 * Reads the channel for incoming header or data frames from client(s).
	 */
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception 
	{
		try
		{
			if (msg instanceof Http2HeadersFrame) 
			{
				onHeadersRead(ctx, (Http2HeadersFrame) msg);
			} 
			
			else if (msg instanceof Http2DataFrame) 
			{
				onDataRead(ctx, (Http2DataFrame) msg);
			} 
			
			else 
			{
				super.channelRead(ctx, msg);
			}
		}
		
		finally
		{
			ReferenceCountUtil.release(msg);
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
	 * Receives data frames from channel and synchronizes that data with its headers for a request once all the data frames has been received.
	 */
	private void onDataRead(ChannelHandlerContext ctx, Http2DataFrame data) throws Exception 
	{				
		String key = data.stream().id() + ctx.channel().id().asLongText();
		
      	requestMap.putIfAbsent(key, new StringBuilder());
      	
      	requestMap.get(key).append(data.content().toString(CharsetUtil.UTF_8));

        if (data.isEndStream()) 
        {	        
	        if(!headerMap.containsKey(key))
	        {
	        	System.out.println("Headers not received for key:  " + key);
	        }
	        
	        else
	        {
                System.out.println("Received Headers:  " + headerMap.get(key) + "\nReceived Payload:  " + requestMap.get(key));

                Http2Headers headers = new DefaultHttp2Headers();
                
                headers.add("key", key);
                
                String responseJson = "{\"response-code\":\"202\",\"response-message\":\"Accepted\"}";
                
                sendResponse(ctx, headers, responseJson);
	        	
	        	headerMap.remove(key);
	        	
	        	requestMap.remove(key);
	        }
        }
	}

	/**
	 * Receives header frame from channel and adds it to headerMap with streamId + channelId as its key.
	 */
	private void onHeadersRead(ChannelHandlerContext ctx, Http2HeadersFrame headers) throws Exception 
	{ 		
		headerMap.put(headers.stream().id() + ctx.channel().id().asLongText(), headers.headers());
	}
	
	/**
	 * Sends HTTP/2 header and data frames to the dedicated channel.
	 * @param ctx
	 * @param headers
	 * @param payload
	 */
	void sendResponse(ChannelHandlerContext ctx, Http2Headers headers, String payload) 
	{
		try
		{			
			ctx.writeAndFlush(new DefaultHttp2HeadersFrame(headers.status(ACCEPTED.codeAsText())));
			
			ctx.writeAndFlush(new DefaultHttp2DataFrame(wrappedBuffer(payload.getBytes()), true));		
		}
		
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	void sendHttpAckResponse(ChannelHandlerContext ctx, HttpResponseStatus status) 
	{
		try 
		{
			System.out.println("Sending HTTP/2 status ACK to client with status code");

			Http2Headers headers = new DefaultHttp2Headers().status(status.codeAsText());
			
			ctx.write(new DefaultHttp2HeadersFrame(headers, true));
		} 
		
		catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
}