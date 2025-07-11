package com.ea.ws.dto;

import io.netty.handler.codec.http.DefaultHttpRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TunnelRequestDTO {
    private DefaultHttpRequest request;
    private volatile StringBuffer requestBody;
}
