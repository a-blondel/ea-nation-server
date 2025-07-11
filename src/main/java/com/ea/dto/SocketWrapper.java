package com.ea.dto;

import com.ea.entities.core.AccountEntity;
import com.ea.entities.core.PersonaConnectionEntity;
import com.ea.entities.core.PersonaEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SocketWrapper {
    private Socket socket;
    private String identifier;
    private volatile String lkey;
    private final AtomicBoolean isHost = new AtomicBoolean(false);
    private final AtomicBoolean isGps = new AtomicBoolean(false);
    private final AtomicBoolean isHosting = new AtomicBoolean(false);
    private volatile AccountEntity accountEntity;
    private volatile PersonaEntity personaEntity;
    private volatile PersonaConnectionEntity personaConnectionEntity;
}
