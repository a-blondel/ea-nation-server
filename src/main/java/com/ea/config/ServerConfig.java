package com.ea.config;

import com.ea.dirtysdk.ProtoSSL;
import com.ea.utils.Props;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ComponentScan("com.ea")
public class ServerConfig {

    private final Props props;
    private final ProtoSSL protoSSL;

    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Initiate the SSL server socket with dynamic subject and issuer
     *
     * @param port     The port number
     * @param subject  The SSL certificate subject
     * @param issuer   The SSL certificate issuer
     * @param certName Unique name for this certificate (typically the server vers)
     * @return SSLServerSocket
     */
    public SSLServerSocket createSslServerSocket(int port, String subject, String issuer, String certName) throws Exception {
        Pair<KeyPair, Certificate> eaCert = protoSSL.getEaCert(subject, issuer);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry(certName, eaCert.getLeft().getPrivate(), "password".toCharArray(), new Certificate[]{eaCert.getRight()});

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "password".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("SSLv3");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
        SSLServerSocket sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(port);

        sslServerSocket.setEnabledProtocols(props.getSslProtocols().split(","));
        sslServerSocket.setEnabledCipherSuites(props.getSslCipherSuites().split(","));

        return sslServerSocket;
    }

    /**
     * Initiate the TCP server socket
     *
     * @return ServerSocket
     */
    public ServerSocket createTcpServerSocket(int port) throws IOException {
        return ServerSocketFactory.getDefault().createServerSocket(port);
    }

}
