package com.ea.ws;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class ResourceController {

    @GetMapping("/images/{path}")
    public ResponseEntity<Resource> serveImage(@PathVariable String path) throws IOException {
        Resource resource = new ClassPathResource("/static/images/" + path);
        return ResponseEntity.ok().contentLength(resource.contentLength()).body(resource);
    }

}
