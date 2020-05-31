package client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.util.CharsetUtil;

/**
 * Handles all the streaming HTTP/2 frame responses.
 * 
 * @author Puspesh.Prakash
 */
public final class Http2ClientStreamFrameResponseHandler extends SimpleChannelInboundHandler<Http2StreamFrame>
{
	private final CountDownLatch latch = new CountDownLatch(1);
	
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) throws Exception
	{
		System.out.println("\nReceived HTTP/2 stream frame: " + msg);

		// isEndStream() is not from a common interface, so both must be checked.
		if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream())
		{
			latch.countDown();
			
			System.out.println("Received data: " + ((Http2DataFrame) msg).content().toString(CharsetUtil.UTF_8) + "\n");
		} 
		
		else if (msg instanceof Http2HeadersFrame)
		{			
			System.out.println("Received headers: " + ((Http2HeadersFrame) msg).headers());
		}
	}

	/**
	 * Waits for the latch to be decremented (i.e. for an end of stream message to
	 * be received), or for the latch to expire after 5 seconds.
	 * 
	 * @return true if a successful HTTP/2 end of stream message was received.
	 */
	public boolean responseSuccessfullyCompleted()
	{
		try
		{
			return latch.await(5, TimeUnit.SECONDS);
		} 
		
		catch (InterruptedException ie)
		{
			System.err.println("Latch exception: " + ie.getMessage());
			
			return false;
		}
	}
}
