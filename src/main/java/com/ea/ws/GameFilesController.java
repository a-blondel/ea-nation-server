package com.ea.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@RestController
public class GameFilesController {

    @GetMapping(value = "tos/", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> getTos() throws IOException {
        log.debug("Received request for Terms of Service");
        Resource resource = new ClassPathResource("legal/tosa.en.txt");
        return ResponseEntity.ok(Files.readString(Path.of(resource.getURI())));
    }

    @GetMapping(value = "tos-ps2/", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> getTosPs2() throws IOException {
        log.debug("Received request for Terms of Service for PS2");
        Resource resource = new ClassPathResource("legal/tosa-ps2.en.txt");
        return ResponseEntity.ok(Files.readString(Path.of(resource.getURI())));
    }

    @GetMapping(value = "news/", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> getNews() throws IOException {
        log.debug("Received request for News");
        Resource resource = new ClassPathResource("legal/news.en.txt");
        return ResponseEntity.ok(Files.readString(Path.of(resource.getURI())));
    }

    @GetMapping(value = "faq/", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> getFaq() throws IOException {
        log.debug("Received request for FAQ");
        Resource resource = new ClassPathResource("legal/faq.en.txt");
        return ResponseEntity.ok(Files.readString(Path.of(resource.getURI())));
    }

    @GetMapping(value = "eaconnect/", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> getEaConnect() throws IOException {
        log.debug("Received request for EA Connect");
        Resource resource = new ClassPathResource("legal/eaconnect.en.txt");
        return ResponseEntity.ok(Files.readString(Path.of(resource.getURI())));
    }


    /**
     * Handles HTTP GET requests for retrieving the roster.
     *
     * @return ResponseEntity containing the roster text as a String.
     */
    @GetMapping(value = "roster", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<byte[]> getRoster() {
        byte[] rosterContent = new byte[1]; // Dummy content
        return ResponseEntity.ok()
                .body(rosterContent);
    }

}
