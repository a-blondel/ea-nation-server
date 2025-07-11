package com.ea.config;

import com.ea.utils.Props;
import com.ea.ws.dto.TunnelRequestDTO;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RequiredArgsConstructor
public class TunnelHandler extends ChannelInboundHandlerAdapter {

    private final Props props;
    private final ConcurrentMap<String, TunnelRequestDTO> requestMap = new ConcurrentHashMap<>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws IOException {
        if (msg instanceof DefaultHttpRequest request) {
            String key = getKey(ctx);
            requestMap.putIfAbsent(key, new TunnelRequestDTO(request, new StringBuffer()));
        } else if (msg instanceof DefaultHttpContent || msg instanceof LastHttpContent) {
            DefaultHttpContent content = null;
            if (msg instanceof DefaultHttpContent) {
                content = (DefaultHttpContent) msg;
            }
            String key = getKey(ctx);
            TunnelRequestDTO requestData = requestMap.get(key);
            if (requestData != null) {
                ByteBuf contentBuffer = content != null ? content.content() : null;
                synchronized (requestData) {
                    if (contentBuffer != null) {
                        requestData.getRequestBody().append(contentBuffer.toString(CharsetUtil.UTF_8));
                    }
                }
                if (msg instanceof LastHttpContent) {
                    synchronized (requestData) {
                        processRequest(ctx, requestData);
                    }
                    requestMap.remove(key);
                }
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private String getKey(ChannelHandlerContext ctx) {
        return ctx.channel().remoteAddress().toString();
    }

    private void processRequest(ChannelHandlerContext ctx, TunnelRequestDTO requestData) throws IOException {
        String uri = requestData.getRequest().uri();
        HttpMethod method = requestData.getRequest().method();
        HttpHeaders headers = requestData.getRequest().headers();

        URL restUrl = new URL("http://localhost:" + props.getServerPort() + uri);
        HttpURLConnection connection = (HttpURLConnection) restUrl.openConnection();
        connection.setRequestMethod(method.name());
        for (Map.Entry<String, String> entry : headers) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        if (method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PATCH)) {
            connection.setDoOutput(true);
            BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream());
            out.write(requestData.getRequestBody().toString().getBytes());
            out.flush();
        }

        int responseCode = connection.getResponseCode();
        byte[] responseBytes;
        if (uri.startsWith("/images/")) {
            InputStream inputStream = connection.getInputStream();
            responseBytes = IOUtils.toByteArray(inputStream);
        } else {
            StringBuilder responseBody;
            try (StringWriter writer = new StringWriter()) {
                IOUtils.copy(connection.getInputStream(), writer, StandardCharsets.UTF_8);
                responseBody = new StringBuilder(writer.toString());
            }
            responseBytes = responseBody.toString().getBytes();
        }

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(responseCode),
                Unpooled.copiedBuffer(responseBytes));

        ctx.write(response);
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        ctx.close();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
