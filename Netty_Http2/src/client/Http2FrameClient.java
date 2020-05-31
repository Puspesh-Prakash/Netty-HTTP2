package client;

import static io.netty.buffer.Unpooled.wrappedBuffer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * An HTTP2 client that allows to send HTTP2 frames to a server using the
 * newer HTTP2 approach (via {@link io.netty.handler.codec.http2.Http2FrameCodec}). When run from the
 * command-line, sends a single headers frame (with prior knowledge) to the
 * server configured at host:port/path.
 * 
 * @author Puspesh.Prakash
 */
public final class Http2FrameClient
{
	static final boolean SSL = false;
	static final String HOST = System.getProperty("host", "127.0.0.1");
	static final int PORT = Integer.parseInt(System.getProperty("port", SSL ? "8443" : "8089"));
	static final String PATH = System.getProperty("path", "/");

	private Http2FrameClient(){}

	public static void main(String[] args) throws Exception
	{
		new Http2FrameClient().initiateClient();
	}
	
	public void initiateClient() throws Exception
	{
		final EventLoopGroup clientWorkerGroup = new NioEventLoopGroup();
		
		final SslContext sslCtx;
		
		if (SSL)
		{
			final SslProvider provider = SslProvider.isAlpnSupported(SslProvider.OPENSSL) ? SslProvider.OPENSSL
					: SslProvider.JDK;
			sslCtx = SslContextBuilder.forClient().sslProvider(provider)
					.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
					// Not recommended for production (Configuration below)
					// example:
					.trustManager(InsecureTrustManagerFactory.INSTANCE)
					.applicationProtocolConfig(new ApplicationProtocolConfig(Protocol.ALPN,
							SelectorFailureBehavior.NO_ADVERTISE, SelectedListenerFailureBehavior.ACCEPT,
							ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1))
					.build();
		} 
		
		else
		{
			sslCtx = null;
		}

		try
		{
			final Bootstrap b = new Bootstrap();
			
			b.group(clientWorkerGroup)
			 .channel(NioSocketChannel.class)
			 .option(ChannelOption.SO_KEEPALIVE, true)
             .option(ChannelOption.SO_SNDBUF, 5242880) //5 MB
             .option(ChannelOption.SO_RCVBUF, 5242880) //5 MB
             .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(3145728, 5242880)) //(low: 3 MB, high: 5 MB)
			 .remoteAddress(HOST, PORT)
			 .handler(new Http2ClientFrameInitializer(sslCtx));

			final Channel channel = b.connect().syncUninterruptibly().channel();
			
			System.out.println("Connected to [" + HOST + ':' + PORT + ']' + "\n");

			final Http2ClientStreamFrameResponseHandler streamFrameResponseHandler = new Http2ClientStreamFrameResponseHandler();

			final Http2StreamChannelBootstrap streamChannelBootstrap = new Http2StreamChannelBootstrap(channel);
			
			final Http2StreamChannel streamChannel = streamChannelBootstrap.open().syncUninterruptibly().getNow();
			
			streamChannel.pipeline().addLast(streamFrameResponseHandler);
			
			new Http2FrameClient().sendPOSTRequest(streamChannel);
			
			System.out.println("HTTP/2 POST request sent to the server.");

			// Wait for the responses (or for the latch to expire), then clean up the connections
			if (!streamFrameResponseHandler.responseSuccessfullyCompleted())
			{
				System.err.println("Did not get HTTP/2 response in expected time.");
			}

			// Wait until the connection is closed.
			channel.close().syncUninterruptibly();
		} 
		
		finally
		{
			clientWorkerGroup.shutdownGracefully();
			
			System.out.println("Finished HTTP/2 request and channel closed successfully.");
		}
	}
	
	/**
	 * Send request (HTTP/2 headers frame - with ':method = GET')
	 * 
	 * @param streamChannel
	 */
	void sendGETRequest(Http2StreamChannel streamChannel)
	{
		final DefaultHttp2Headers headers = new DefaultHttp2Headers();
		
		headers.method("GET");
		headers.path(PATH);
		headers.scheme(SSL ? "https" : "http");
		
		final Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers);
		
		streamChannel.writeAndFlush(headersFrame);	
	}

	/**
	 * Send request (HTTP/2 headers frame - with ':method = POST' and HTTP/2 data frame)
	 * 
	 * @param streamChannel
	 */
	void sendPOSTRequest(Http2StreamChannel streamChannel)
	{
		final DefaultHttp2Headers headers = new DefaultHttp2Headers();
		
		headers.method("POST");
		headers.path(PATH);
		headers.scheme(SSL ? "https" : "http");
		
		final Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers);
		
		streamChannel.writeAndFlush(headersFrame);	
		
		String requestJson = "{\"array\":[1,7],\"http2\":true,\"type\":\"json\",\"version\":4,\"object\":{\"a\":\"b\",\"c\":\"d\"},\"title\":\"Netty Http2 Client & Server!\"}";
		
		final Http2DataFrame dataFrame = new DefaultHttp2DataFrame(wrappedBuffer(requestJson.getBytes()), true);
		
		streamChannel.writeAndFlush(dataFrame);
	}
}