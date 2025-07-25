package com.ea.ws;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class GameFilesController {

    /**
     * Handles HTTP GET requests for retrieving the EA terms of service (TOS).
     *
     * @return ResponseEntity containing the TOS text as a String.
     * @throws IOException if an error occurs while reading the TOS file.
     */
    @GetMapping(value = "legalapp/webterms/us/fr/pc/", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> getTos() throws IOException {
        Resource resource = new ClassPathResource("tosa.en.txt");
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
