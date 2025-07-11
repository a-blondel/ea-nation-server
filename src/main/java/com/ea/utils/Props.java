package com.ea.utils;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Component
public class Props {

    @Value("${server.port}")
    private int serverPort;

    @Value("${http.port}")
    private int httpPort;

    @Value("${http.dns_name}")
    private String dnsName;

    @Value("${spring.mail.username}")
    private String emailUsername;

    @Value("${ssl.protocols}")
    private String sslProtocols;

    @Value("${ssl.cipher-suites}")
    private String sslCipherSuites;

    @Value("${ssl.certificate.cipher-algorithm}")
    private String sslCertificateCipherAlgorithm;

    @Value("${ssl.debug.enabled}")
    private boolean sslDebugEnabled;

    @Value("${tcp.host}")
    private String tcpHost;

    @Value("${tcp.buddy-port}")
    private int tcpBuddyPort;

    @Value("${tcp.debug.enabled}")
    private boolean tcpDebugEnabled;

    @Value("${tcp.debug.exclusions}")
    private List<String> tcpDebugExclusions;

    @Value("${ssc2.key}")
    private String ssc2Key;

}
