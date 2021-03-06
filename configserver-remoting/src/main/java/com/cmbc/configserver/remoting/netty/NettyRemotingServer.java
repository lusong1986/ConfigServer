package com.cmbc.configserver.remoting.netty;

import com.cmbc.configserver.common.ThreadFactoryImpl;
import com.cmbc.configserver.remoting.ChannelEventListener;
import com.cmbc.configserver.remoting.InvokeCallback;
import com.cmbc.configserver.remoting.RPCHook;
import com.cmbc.configserver.remoting.RemotingServer;
import com.cmbc.configserver.remoting.common.Pair;
import com.cmbc.configserver.remoting.common.RemotingHelper;
import com.cmbc.configserver.remoting.common.RemotingUtil;
import com.cmbc.configserver.remoting.common.RequestProcessor;
import com.cmbc.configserver.remoting.exception.RemotingSendRequestException;
import com.cmbc.configserver.remoting.exception.RemotingTimeoutException;
import com.cmbc.configserver.remoting.exception.RemotingTooMuchRequestException;
import com.cmbc.configserver.remoting.protocol.RemotingCommand;
import com.cmbc.configserver.utils.ConfigServerLogger;
import com.cmbc.configserver.utils.NetUtils;
import com.cmbc.configserver.utils.StatisticsLog;
import com.cmbc.configserver.utils.ThreadUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class NettyRemotingServer extends NettyRemotingAbstract implements RemotingServer {
	private static final Logger log = LoggerFactory.getLogger(NettyRemotingServer.class);
	private final ServerBootstrap serverBootstrap;
	private final EventLoopGroup eventLoopGroupWorker;
	private final EventLoopGroup eventLoopGroupBoss;
	private final NettyServerConfig nettyServerConfig;
	// the executor is using to process the callback ACK
	private final ExecutorService publicExecutor;
	private final ChannelEventListener channelEventListener;
	// timer
	private final Timer timer = new Timer("ServerHouseKeepingService", true);
	private DefaultEventExecutorGroup defaultEventExecutorGroup;
	private RPCHook rpcHook;
	// the listening port of the server
	private int port = 0;
    /**
     * the total connection number of the config server
     */
    private final AtomicInteger totalConnectionNumber = new AtomicInteger(0);

	public NettyRemotingServer(final NettyServerConfig nettyServerConfig) {
		this(nettyServerConfig, null);
	}

	public NettyRemotingServer(final NettyServerConfig nettyServerConfig,final ChannelEventListener channelEventListener) {
		super(nettyServerConfig.getServerOnewaySemaphoreValue(),nettyServerConfig.getServerAsyncSemaphoreValue());
		this.serverBootstrap = new ServerBootstrap();
		this.nettyServerConfig = nettyServerConfig;
		this.channelEventListener = channelEventListener;

		int publicThreadNumbers = nettyServerConfig.getServerCallbackExecutorThreads();
		if (publicThreadNumbers <= 0) {
			publicThreadNumbers = 4;
		}

		this.publicExecutor = Executors.newFixedThreadPool(publicThreadNumbers,new ThreadFactoryImpl("NettyServerPublicExecutor-"));

		this.eventLoopGroupBoss = new NioEventLoopGroup(1,new ThreadFactoryImpl("NettyBossSelector-"));

        this.eventLoopGroupWorker = new NioEventLoopGroup(nettyServerConfig.getServerSelectorThreads(),
                new ThreadFactoryImpl(String.format("NettyServerSelector-%d-", nettyServerConfig.getServerSelectorThreads())));
    }

	@Override
	public void start() {
        StatisticsLog.registerExecutor("server-public-pool",(ThreadPoolExecutor)this.publicExecutor);
		this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(nettyServerConfig.getServerWorkerThreads(), new ThreadFactoryImpl("NettyServerWorkerThread-"));

        if (!NetUtils.isValidPort(this.nettyServerConfig.getListenPort())) {
            throw new IllegalArgumentException(String.format("invalid listening port %s of the config server", this.nettyServerConfig.getListenPort()));
        }

		ServerBootstrap childHandler = this.serverBootstrap.group(this.eventLoopGroupBoss, this.eventLoopGroupWorker)
				.channel(NioServerSocketChannel.class)
				.option(ChannelOption.SO_BACKLOG, 1024)
				.option(ChannelOption.SO_REUSEADDR, true)
				.childOption(ChannelOption.TCP_NODELAY, true)
				.childOption(ChannelOption.SO_SNDBUF,NettySystemConfig.SocketSndbufSize)
				.childOption(ChannelOption.SO_RCVBUF, NettySystemConfig.SocketRcvbufSize)
				.localAddress(new InetSocketAddress(NetUtils.getLocalAddress(),this.nettyServerConfig.getListenPort()))
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(
								defaultEventExecutorGroup,
								new NettyEncoder(),
								new NettyDecoder(),
								new IdleStateHandler(0, 0, nettyServerConfig.getServerChannelMaxIdleTimeSeconds()),
								new NettyConnectManageHandler(),
								new NettyServerHandler());
					}
				});

        if (NettySystemConfig.NettyPooledByteBufAllocatorEnable) {
            //this option may occupy too much no-heap memory.
            //the option is close in default.
            childHandler.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        }

		try {
			ChannelFuture sync = this.serverBootstrap.bind().sync();
			InetSocketAddress address = (InetSocketAddress) sync.channel().localAddress();
			this.port = address.getPort();
		} catch (InterruptedException ex) {
			throw new RuntimeException("serverBootstrap.bind().sync() InterruptedException",ex);
		}

		if (this.channelEventListener != null) {
			this.nettyEventExecutor.start();
		}

		// schedule the timeout of async call per second
		this.timer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				try {
					NettyRemotingServer.this.scanResponseTable();
				} catch (Exception e) {
					log.error("scanResponseTable exception", e);
				}
			}
		}, 1000 * 3, 1000);
	}

	@Override
	public void registerProcessor(int requestCode, RequestProcessor processor,
			ExecutorService executor) {
		ExecutorService executorThis = executor;
		if (null == executor) {
			executorThis = this.publicExecutor;
		}

		Pair<RequestProcessor, ExecutorService> pair = new Pair<RequestProcessor, ExecutorService>(processor, executorThis);
		this.processorTable.put(requestCode, pair);
	}

	@Override
	public void registerDefaultProcessor(RequestProcessor processor,
			ExecutorService executor) {
		this.defaultRequestProcessor = new Pair<RequestProcessor, ExecutorService>(
				processor, executor);
	}

	@Override
	public RemotingCommand invokeSync(final Channel channel,
			final RemotingCommand request, final long timeoutMillis)
			throws InterruptedException, RemotingSendRequestException,
			RemotingTimeoutException {
		return this.invokeSyncImpl(channel, request, timeoutMillis);
	}

	@Override
	public void invokeAsync(Channel channel, RemotingCommand request,
			long timeoutMillis, InvokeCallback invokeCallback)
			throws InterruptedException, RemotingTooMuchRequestException,
			RemotingTimeoutException, RemotingSendRequestException {
		this.invokeAsyncImpl(channel, request, timeoutMillis, invokeCallback);
	}

	@Override
	public void invokeOneway(Channel channel, RemotingCommand request,
			long timeoutMillis) throws InterruptedException,
			RemotingTooMuchRequestException, RemotingTimeoutException,
			RemotingSendRequestException {
		this.invokeOnewayImpl(channel, request, timeoutMillis);
	}

	@Override
	public void shutdown() {
		try {
            this.timer.cancel();
			this.eventLoopGroupBoss.shutdownGracefully();

			this.eventLoopGroupWorker.shutdownGracefully();

            this.nettyEventExecutor.shutdown();

            if (this.defaultEventExecutorGroup != null) {
				this.defaultEventExecutorGroup.shutdownGracefully();
			}
		} catch (Exception e) {
			log.error("NettyRemotingServer shutdown exception, ", e);
		}

		if (this.publicExecutor != null) {
			try {
                ThreadUtils.shutdownAndAwaitTermination(this.publicExecutor);
			} catch (Exception e) {
				log.error("NettyRemotingServer shutdown exception, ", e);
			}
		}
	}

	@Override
	public ChannelEventListener getChannelEventListener() {
		return channelEventListener;
	}

	@Override
	public ExecutorService getCallbackExecutor() {
		return this.publicExecutor;
	}

	class NettyServerHandler extends SimpleChannelInboundHandler<RemotingCommand> {

		@Override
		protected void channelRead0(ChannelHandlerContext ctx,RemotingCommand msg) throws Exception {
			processMessageReceived(ctx, msg);
		}
	}

	class NettyConnectManageHandler extends ChannelDuplexHandler {
		@Override
		public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
			final String remoteAddress = RemotingHelper.parseChannelRemoteAddress(ctx.channel());
			log.info("NettyConnectManageHandler channelRegistered {}",remoteAddress);
			super.channelRegistered(ctx);
		}

		@Override
		public void channelUnregistered(ChannelHandlerContext ctx)	throws Exception {
			final String remoteAddress = RemotingHelper.parseChannelRemoteAddress(ctx.channel());
			log.info("NettyConnectManageHandler channelUnregistered, the channel {}",remoteAddress);
			super.channelUnregistered(ctx);
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
            int connectionCount = totalConnectionNumber.incrementAndGet();
            String channelId = RemotingHelper.getChannelId(ctx.channel());
            if (connectionCount > nettyServerConfig.getServerMaxConnectionNumbers()) {
                //close the channel
                RemotingUtil.closeChannel(ctx.channel());
                //throw an runtime exception
                throw new RuntimeException(String.format("channel connection exceeds the max_connection_number %s. close the channel %s", nettyServerConfig.getServerMaxConnectionNumbers(), channelId));
            }
            ConfigServerLogger.info(String.format("channel %s is connected to server. the total connection count is %s", channelId, connectionCount));

            final String remoteAddress = RemotingHelper.parseChannelRemoteAddress(ctx.channel());
            log.info("NettyConnectManageHandler channelActive, the channel {}", remoteAddress);
            super.channelActive(ctx);

            if (NettyRemotingServer.this.channelEventListener != null) {
                NettyRemotingServer.this.putNettyEvent(new NettyEvent(
                        NettyEventType.ACTIVE, remoteAddress, ctx
                        .channel(), null));
            }
        }

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			final String remoteAddress = RemotingHelper.parseChannelRemoteAddress(ctx.channel());
			log.info("NettyConnectManageHandler channelInactive, the channel {}",remoteAddress);
			super.channelInactive(ctx);
            decrementConnection(ctx.channel());
			if (NettyRemotingServer.this.channelEventListener != null) {
				NettyRemotingServer.this.putNettyEvent(new NettyEvent(
						NettyEventType.CLOSE, remoteAddress, ctx
								.channel(),null));
			}
		}

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object evt)	throws Exception {
			if (evt instanceof IdleStateEvent) {
				IdleStateEvent event = (IdleStateEvent) evt;
				if (event.state().equals(IdleState.ALL_IDLE)) {
					final String remoteAddress = RemotingHelper
							.parseChannelRemoteAddress(ctx.channel());
					log.warn("NettyConnectManageHandler IDLE exception {}",
							remoteAddress);
					if (NettyRemotingServer.this.channelEventListener != null) {
						NettyRemotingServer.this.putNettyEvent(new NettyEvent(
								NettyEventType.IDLE, remoteAddress,
								ctx.channel(),null));
					}
				}
			}

			ctx.fireUserEventTriggered(evt);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)	throws Exception {
			final String remoteAddress = RemotingHelper
					.parseChannelRemoteAddress(ctx.channel());
			log.warn("NettyConnectManageHandler exceptionCaught remoteAddress {} exception {}.",remoteAddress,cause);

			if (NettyRemotingServer.this.channelEventListener != null) {
				NettyRemotingServer.this.putNettyEvent(new NettyEvent(
						NettyEventType.EXCEPTION, remoteAddress, ctx
								.channel(),cause));
			}
		}

        private void decrementConnection(Channel channel){
            int connectionCount = totalConnectionNumber.decrementAndGet();
            //reset the connection number
            if(totalConnectionNumber.get() < 0 ){
                totalConnectionNumber.set(0);
            }
            ConfigServerLogger.info(String.format("channel %s is closed from server. the total connection count is %s", RemotingHelper.getChannelId(channel), connectionCount));
        }
	}

	@Override
	public void registerRPCHook(RPCHook rpcHook) {
		this.rpcHook = rpcHook;
	}

	@Override
	public RPCHook getRPCHook() {
		return this.rpcHook;
	}

	@Override
	public int localPort() {
		return this.port;
	}

    /**
     * get the connection count of the config server
     * @return the current connection count
     */
    public int getConnectionCount(){
        return this.totalConnectionNumber.get();
    }
}