package com.ea.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class HttpRequestData {
    private String method;
    private String uri;
    private String httpVersion;
    private Map<String, String> headers;
    private String body;
}
