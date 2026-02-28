package eu.eahub;

import eu.eahub.config.*;
import eu.eahub.services.core.GameService;
import eu.eahub.services.core.PersonaService;
import eu.eahub.services.core.RoomService;
import eu.eahub.services.core.UserSetService;
import eu.eahub.services.server.GameServerService;
import eu.eahub.services.server.SocketManager;
import eu.eahub.steps.SocketReader;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.Props;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Security;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
public class ServerApp implements CommandLineRunner {

    private final ScheduledExecutorService dataCleanupThread = Executors.newSingleThreadScheduledExecutor();
    private final Props props;
    private final ServerConfig serverConfig;
    private final GameServerService gameServerService;
    private final SocketManager socketManager;
    private final SocketReader socketReader;
    private final SocketWriter socketWriter;
    private final PersonaService personaService;
    private final GameService gameService;
    private final RoomService roomService;
    private final UserSetService userSetService;
    private ExecutorService clientHandlingExecutor = Executors.newFixedThreadPool(500);

    @Value("${http.tunnel.enabled:true}")
    private boolean tunnelEnabled;

    public static void main(String[] args) {
        SpringApplication.run(ServerApp.class, args);
    }

    @Override
    public void run(String... args) {
        // System and security properties
        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        System.setProperty("https.protocols", props.getSslProtocols());
        if (props.isSslDebugEnabled()) {
            System.setProperty("javax.net.debug", "all");
        }

        // Server configuration
        setupThreadPool();
        if (tunnelEnabled) {
            startTcpTunnelServer();
        }
        addGracefulExitOnShutdown();

        // Data integrity
        startDataCleanupThread();

        // Generate rooms
        roomService.generateRooms();

        try {
            for (GameServerConfig.GameServer gameServer : gameServerService.getEnabledServers()) {
                startGameServer(gameServer);
            }
        } catch (Exception e) {
            log.error("Error starting servers", e);
        }
    }

    private void startGameServer(GameServerConfig.GameServer gameServer) throws Exception {
        for (GameServerConfig.PortConfig portConfig : gameServer.getPorts()) {
            // TCP server
            ServerSocket tcpServerSocket = serverConfig.createTcpServerSocket(portConfig.getPort());
            startServerThread(tcpServerSocket, this::createTcpSocketThread, gameServer.isAries());
            log.info("Started TCP server for {} on port {}", gameServer.getName(), portConfig.getPort());

            // SSL server
            if (gameServer.getSsl() != null && gameServer.getSsl().isEnabled() && gameServer.getSsl().getDomain() != null) {
                int sslPort = portConfig.getPort() + 1;
                String subject = gameServerService.generateSslSubject(gameServer.getSsl().getDomain());
                String issuer = gameServerService.getSslIssuer();

                SSLServerSocket sslServerSocket = serverConfig.createSslServerSocket(sslPort, subject, issuer, gameServer.getName());
                startServerThread(sslServerSocket, this::createSslSocketThread, true);
                log.info("Started SSL server for {} on port {}", gameServer.getName(), sslPort);
            }
        }
    }

    private void startServerThread(ServerSocket serverSocket, Function<Socket, Runnable> runnableFactory, boolean isAries) {
        new Thread(() -> {
            try {
                while (true) {
                    Socket socket = serverSocket.accept();
                    if (!(socket instanceof SSLSocket)) {
                        if (isAries) {
                            socketManager.addSocket(socket.getRemoteSocketAddress().toString(), socket);
                        } else {
                            socketManager.addBuddySocket(socket.getRemoteSocketAddress().toString(), socket);
                        }
                    }
                    clientHandlingExecutor.submit(runnableFactory.apply(socket));
                }
            } catch (IOException e) {
                log.error("Error accepting connections on port: {}", serverSocket.getLocalPort(), e);
            }
        }).start();
    }

    private void startTcpTunnelServer() {
        new Thread(() -> {
            log.info("Starting tunnel server on port {}", props.getHttpPort());
            EventLoopGroup bossGroup = new NioEventLoopGroup();
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<>() {
                            @Override
                            protected void initChannel(Channel channel) {
                                ChannelPipeline pipeline = channel.pipeline();
                                pipeline.addLast(new HttpServerCodec());
                                pipeline.addLast(new TunnelHandler(props));
                            }
                        });
                ChannelFuture channelFuture = serverBootstrap.bind(props.getHttpPort()).sync();
                channelFuture.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }).start();
    }

    private Runnable createTcpSocketThread(Socket socket) {
        return new TcpSocketThread(socket, socketManager, socketReader, socketWriter, personaService, gameService, userSetService);
    }

    private Runnable createSslSocketThread(Socket socket) {
        return new SslSocketThread((SSLSocket) socket, socketReader);
    }

    private void addGracefulExitOnShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            dataCleanupThread.shutdown();
            clientHandlingExecutor.shutdown();
            try {
                if (!dataCleanupThread.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    dataCleanupThread.shutdownNow();
                }
                if (!clientHandlingExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    clientHandlingExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                dataCleanupThread.shutdownNow();
                clientHandlingExecutor.shutdownNow();
            }
        }));
    }

    private void startDataCleanupThread() {
        dataCleanupThread.scheduleAtFixedRate(() -> {
            try {
                gameService.dataCleanup();
            } catch (Exception e) {
                log.error("Error during data cleanup", e);
            }
        }, 5, 60, TimeUnit.SECONDS);
    }

    private void setupThreadPool() {
        int poolSize = 500;
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(600);
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

        clientHandlingExecutor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                queue,
                threadFactory,
                handler
        );
    }

}
