package server;


import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.NettyRuntime;

/**
 * @author Puspesh.Prakash
 * 
 * A HTTP/2 Server that responds to requests with an event. 
 *
 * <p> This class is making use of the "multiplexing" http2 API, where streams are mapped to child Channels. 
 */

public final class Http2Server 
{
    static final boolean SSL = System.getProperty("ssl") != null;

    static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8089"));

    public static void main(String[] args) throws Exception 
    {
    	final SslContext sslCtx;
        
        if (SSL) 
        {
            SslProvider provider = SslProvider.isAlpnSupported(SslProvider.OPENSSL) ? SslProvider.OPENSSL : SslProvider.JDK;
            
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
					                  .sslProvider(provider)
					                  /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
					                   * Please refer to the HTTP/2 specification for cipher requirements. */
					                  .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
					                  .applicationProtocolConfig(new ApplicationProtocolConfig(Protocol.ALPN,
					                     // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
					                     SelectorFailureBehavior.NO_ADVERTISE,
					                     // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
					                     SelectedListenerFailureBehavior.ACCEPT, ApplicationProtocolNames.HTTP_2, 
					                     ApplicationProtocolNames.HTTP_1_1))
					                  .build();
        } 
        
        else 
        {
            sslCtx = null;
        }
    	
        ChannelFuture f = null;
        
        EventLoopGroup parentGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		
		System.out.println("Available Threads: " + NettyRuntime.availableProcessors() * 2);
        
        try 
        {
            ServerBootstrap b = new ServerBootstrap();

            b.group(parentGroup, workerGroup)
             .option(ChannelOption.SO_BACKLOG, 4096)
             .option(ChannelOption.SO_RCVBUF, 5242880) //5 MB
             .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(3145728, 5242880)) //(low: 3 MB, high: 5 MB)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new Http2ServerInitializer(sslCtx));
            
            f = b.bind(new InetSocketAddress("127.0.0.1", 8089)).sync();
            
			System.out.println("Server running on " + f.channel().localAddress());
        } 
        
        catch (Exception e) 
        {
        	e.printStackTrace();
		}
        
        finally 
        {
        	try 
        	{
        		f.channel().closeFuture().sync();
        		
				parentGroup.shutdownGracefully().sync();
				
				workerGroup.shutdownGracefully().sync();
			} 
        	
        	catch (InterruptedException e) 
        	{
				e.printStackTrace();
			}
        }
    }
}
