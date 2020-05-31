package server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodecFactory;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;

/**
 * @author Puspesh.Prakash
 * 
 *  <p>    Sets up the Netty pipeline for the example server. 
 *  	   Depending on the end-point configuration, sets up the pipeline for NPN or clear-text HTTP upgrade to HTTP/2.
 */
public class Http2ServerInitializer extends ChannelInitializer<SocketChannel>
{
    private final SslContext sslCtx;
    private final int maxHttpContentLength;

    public Http2ServerInitializer(SslContext sslCtx) 
    {
        this(sslCtx, Integer.MAX_VALUE);
    }

    public Http2ServerInitializer(SslContext sslCtx, int maxHttpContentLength) 
    {
        if (maxHttpContentLength < 0) 
        {
            throw new IllegalArgumentException("maxHttpContentLength (expected >= 0): " + maxHttpContentLength);
        }
        
        this.sslCtx = sslCtx;
        this.maxHttpContentLength = maxHttpContentLength;
    }
    
	
	@Override
	public void initChannel(SocketChannel ch)
	{
		if (sslCtx != null) 
        {
            configureSsl(ch);
        } 
        
        else 
        {
        	// Pipeline for clear-text upgrade from HTTP to HTTP/2.0
//            configureClearTextHttp1(ch);
        	
        	// Pipeline for direct HTTP/2.0 requests.
        	configureClearText(ch); 
        }
	}

	private void configureClearText(SocketChannel ch)
	{
		Http2FrameCodecBuilder frameCodecBuilder = Http2FrameCodecBuilder.forServer().initialSettings(Http2Settings.defaultSettings()
																		 .maxFrameSize(5242880).initialWindowSize(5242880)) 
																		 .autoAckPingFrame(true);

		ch.pipeline().addLast(frameCodecBuilder.build(), new Http2MultiplexHandler(new Http2EventReceiverHandler()));
	}

    /**
     * Configures the pipeline for TLS NPN negotiation to HTTP/2.
     */
    private void configureSsl(SocketChannel ch) 
    {
        ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()), new Http2OrHttpHandler());
    }
    
	private static final UpgradeCodecFactory upgradeCodecFactory = new UpgradeCodecFactory() 
	{
        @Override
        public UpgradeCodec newUpgradeCodec(CharSequence protocol) 
        {
            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) 
            {
                return new Http2ServerUpgradeCodec(Http2FrameCodecBuilder.forServer().build(),
                         new Http2MultiplexHandler(new Http2EventReceiverHandler()));
            } 
            
            else 
            {
                return null;
            }
        }
    };
    
    /**
     * Configures the pipeline for a clear-text upgrade from HTTP to HTTP/2.0
     */
    @SuppressWarnings("unused")
	private void configureClearTextHttp1(SocketChannel ch) 
    {
        final ChannelPipeline p = ch.pipeline();
        
        final HttpServerCodec sourceCodec = new HttpServerCodec();

        p.addLast(sourceCodec);
        
        p.addLast(new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory));
        
        p.addLast(new SimpleChannelInboundHandler<HttpMessage>() 
        {
           @Override
           protected void channelRead0(ChannelHandlerContext ctx, HttpMessage msg) throws Exception 
           {
               // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
               System.err.println("Directly talking: " + msg.protocolVersion() + " (no upgrade was attempted)");
               
               ChannelPipeline pipeline = ctx.pipeline();
//               pipeline.addAfter(ctx.name(), null, new HelloWorldHttp2Handler("Direct. No Upgrade Attempted."));
               pipeline.replace(this, null, new HttpObjectAggregator(maxHttpContentLength));
               
               ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
           }
       });
   }
}
