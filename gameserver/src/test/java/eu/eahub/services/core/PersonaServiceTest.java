package eu.eahub.services.core;

import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.AccountEntity;
import eu.eahub.entities.core.PersonaConnectionEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.repositories.buddy.FeedbackRepository;
import eu.eahub.repositories.buddy.FeedbackTypeRepository;
import eu.eahub.repositories.core.AccountRepository;
import eu.eahub.repositories.core.PersonaConnectionRepository;
import eu.eahub.repositories.core.PersonaRepository;
import eu.eahub.services.server.SocketManager;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.PersonaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonaServiceTest {

    private final PersonaRepository personaRepository = mock(PersonaRepository.class);
    private final PersonaConnectionRepository personaConnectionRepository = mock(PersonaConnectionRepository.class);
    private final SocketWriter socketWriter = mock(SocketWriter.class);
    private final SocketManager socketManager = mock(SocketManager.class);
    private PersonaService personaService;
    private Socket socket;

    @BeforeEach
    void setUp() throws Exception {
        personaService = new PersonaService(mock(AccountRepository.class), personaRepository, personaConnectionRepository, socketWriter,
                socketManager, mock(FeedbackRepository.class), mock(FeedbackTypeRepository.class), mock(RoomService.class),
                mock(PersonaUtils.class));
        socket = mock(Socket.class);
        when(socket.getInetAddress()).thenReturn(InetAddress.getLoopbackAddress());
        when(personaConnectionRepository.findByVersAndPersonaPersAndIsHostFalseAndEndTimeIsNull(anyString(), anyString())).thenReturn(List.of());
    }

    private static AccountEntity account(long id) {
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setLoc("enUS");
        return account;
    }

    private static PersonaEntity persona(String name, AccountEntity account) {
        PersonaEntity persona = new PersonaEntity();
        persona.setId(10L);
        persona.setPers(name);
        persona.setAccount(account);
        return persona;
    }

    private SocketWrapper loggedIn(AccountEntity account) {
        SocketWrapper socketWrapper = new SocketWrapper();
        socketWrapper.setIdentifier("/192.0.2.10:1234");
        socketWrapper.setAccountEntity(account);
        PersonaConnectionEntity personaConnection = new PersonaConnectionEntity();
        personaConnection.setVers("PS2_NHL08");
        socketWrapper.setPersonaConnectionEntity(personaConnection);
        return socketWrapper;
    }

    private String reply() {
        ArgumentCaptor<SocketData> captor = ArgumentCaptor.forClass(SocketData.class);
        verify(socketWriter, atLeastOnce()).write(any(), captor.capture());
        return captor.getAllValues().getFirst().getIdMessage();
    }

    @Test
    void personaOfAnotherAccountIsRefused() {
        when(personaRepository.findByPers("Victim")).thenReturn(Optional.of(persona("Victim", account(2))));
        SocketWrapper socketWrapper = loggedIn(account(1));

        personaService.pers(socket, new SocketData("pers", "PERS=Victim", null), socketWrapper);

        assertEquals("persmaut", reply());
        assertEquals(null, socketWrapper.getPersonaEntity());
        verify(personaConnectionRepository, never()).findByVersAndPersonaPersAndIsHostFalseAndEndTimeIsNull(anyString(), anyString());
        verify(personaConnectionRepository, never()).save(any());
    }

    @Test
    void personaWithoutLoggedInAccountIsRefused() {
        when(personaRepository.findByPers("Victim")).thenReturn(Optional.of(persona("Victim", account(2))));

        personaService.pers(socket, new SocketData("pers", "PERS=Victim", null), loggedIn(null));

        assertEquals("persmaut", reply());
    }

    @Test
    void ownPersonaIsSelected() {
        AccountEntity account = account(1);
        PersonaEntity persona = persona("Mine", account);
        when(personaRepository.findByPers("Mine")).thenReturn(Optional.of(persona));
        SocketWrapper socketWrapper = loggedIn(account);

        personaService.pers(socket, new SocketData("pers", "PERS=Mine", null), socketWrapper);

        assertEquals("pers", reply());
        assertEquals(persona, socketWrapper.getPersonaEntity());
    }

    @Test
    void dedicatedHostNameIsCheckedWithoutTheAt() {
        when(personaRepository.findByPers("Victim")).thenReturn(Optional.of(persona("Victim", account(2))));

        personaService.pers(socket, new SocketData("pers", "PERS=Vic@tim", null), loggedIn(account(1)));

        assertEquals("persmaut", reply());
    }
}
