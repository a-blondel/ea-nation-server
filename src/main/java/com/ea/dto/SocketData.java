package com.ea.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class SocketData {

    private String idMessage;
    private String inputMessage;
    private Map<String, String> outputData;

}
