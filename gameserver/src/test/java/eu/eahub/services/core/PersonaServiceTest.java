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

import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonaServiceTest {

    private final PersonaRepository personaRepository = mock(PersonaRepository.class);
    private final PersonaConnectionRepository personaConnectionRepository = mock(PersonaConnectionRepository.class);
    private final SocketManager socketManager = mock(SocketManager.class);
    private PersonaService personaService;
    private Socket socket;
    private SocketWrapper socketWrapper;

    @BeforeEach
    void setUp() throws Exception {
        personaService = new PersonaService(mock(AccountRepository.class), personaRepository, personaConnectionRepository, mock(SocketWriter.class),
                socketManager, mock(FeedbackRepository.class), mock(FeedbackTypeRepository.class), mock(RoomService.class),
                mock(PersonaUtils.class));
        socket = mock(Socket.class);
        when(socket.getInetAddress()).thenReturn(InetAddress.getLoopbackAddress());

        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setLoc("enUS");
        PersonaEntity persona = new PersonaEntity();
        persona.setId(10L);
        persona.setPers("Player");
        persona.setAccount(account);
        when(personaRepository.findByPers("Player")).thenReturn(Optional.of(persona));

        socketWrapper = new SocketWrapper();
        socketWrapper.setIdentifier("/127.0.0.1:2000");
        socketWrapper.setAccountEntity(account);
        socketWrapper.setPersonaConnectionEntity(connection(2L));
    }

    private static PersonaConnectionEntity connection(Long id) {
        PersonaConnectionEntity personaConnection = new PersonaConnectionEntity();
        personaConnection.setId(id);
        personaConnection.setVers("PS2_NHL08");
        // A localhost client's address is stored rewritten, so it never matches a socket identifier
        personaConnection.setAddress("/192.168.1.5:1000");
        return personaConnection;
    }

    private void alreadyConnected(PersonaConnectionEntity personaConnection) {
        when(personaConnectionRepository.findByVersAndPersonaPersAndIsHostFalseAndEndTimeIsNull(anyString(), anyString()))
                .thenReturn(List.of(personaConnection));
    }

    @Test
    void oldSocketIsFoundByConnectionIdAndClosed() throws Exception {
        PersonaConnectionEntity old = connection(1L);
        alreadyConnected(old);
        Socket oldSocket = mock(Socket.class);
        SocketWrapper oldSocketWrapper = new SocketWrapper();
        oldSocketWrapper.setSocket(oldSocket);
        when(socketManager.getSocketWrapperByPersonaConnectionId(1L)).thenReturn(oldSocketWrapper);

        personaService.pers(socket, new SocketData("pers", "PERS=Player", null), socketWrapper);

        verify(oldSocket).close();
        assertNotNull(socketWrapper.getPersonaEntity());
    }

    @Test
    void oldConnectionWithoutSocketIsEnded() {
        PersonaConnectionEntity old = connection(1L);
        alreadyConnected(old);

        personaService.pers(socket, new SocketData("pers", "PERS=Player", null), socketWrapper);

        assertNotNull(old.getEndTime());
        verify(personaConnectionRepository).save(old);
        assertNotNull(socketWrapper.getPersonaEntity());
    }

    @Test
    void ownConnectionIsNotEnded() {
        PersonaConnectionEntity own = socketWrapper.getPersonaConnectionEntity();
        alreadyConnected(own);

        personaService.pers(socket, new SocketData("pers", "PERS=Player", null), socketWrapper);

        assertNull(own.getEndTime());
        verify(socketManager, never()).getSocketWrapperByPersonaConnectionId(2L);
    }
}
