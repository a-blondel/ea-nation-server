package eu.eahub.services.core;

import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.AccountEntity;
import eu.eahub.mappers.SocketMapper;
import eu.eahub.repositories.core.AccountRepository;
import eu.eahub.repositories.core.BlacklistRepository;
import eu.eahub.services.server.GameServerService;
import eu.eahub.steps.SocketWriter;
import eu.eahub.utils.EmailUtils;
import eu.eahub.utils.PasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

class AccountServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PasswordUtils passwordUtils = mock(PasswordUtils.class);
    private final SocketWriter socketWriter = mock(SocketWriter.class);
    private AccountService accountService;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(passwordUtils, mock(SocketMapper.class), accountRepository, mock(BlacklistRepository.class),
                mock(PersonaService.class), mock(GameServerService.class), socketWriter, mock(EmailUtils.class));
        account = account(1);
        when(accountRepository.findByName("player")).thenReturn(Optional.of(account));
        when(accountRepository.findByMail(anyString())).thenReturn(List.of(account));
        when(passwordUtils.bCryptMatches("secret", "hash")).thenReturn(true);
        when(passwordUtils.bCryptEncode("changed")).thenReturn("changed-hash");
    }

    private static AccountEntity account(long id) {
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setName("player");
        account.setPass("hash");
        account.setMail("player@example.com");
        return account;
    }

    private static SocketWrapper loggedIn(AccountEntity account) {
        SocketWrapper socketWrapper = new SocketWrapper();
        socketWrapper.setAccountEntity(account);
        return socketWrapper;
    }

    private String edit(String input, SocketWrapper socketWrapper) {
        accountService.edit(mock(Socket.class), new SocketData("edit", input, null), socketWrapper);
        ArgumentCaptor<SocketData> captor = ArgumentCaptor.forClass(SocketData.class);
        verify(socketWriter, atLeastOnce()).write(any(), captor.capture());
        return captor.getValue().getIdMessage();
    }

    @Test
    void loggedInAccountIsUpdatedWithoutPassword() {
        assertEquals("edit", edit("NAME=player\nMAIL=new@example.com\nSPAM=NN", loggedIn(account)));

        assertEquals("new@example.com", account.getMail());
        assertEquals("hash", account.getPass());
        verify(accountRepository).save(account);
    }

    @Test
    void anotherAccountNeedsItsPassword() {
        assertEquals("editpass", edit("NAME=player\nMAIL=new@example.com\nPASS=guess\nCHNG=guess", loggedIn(account(2))));
        assertEquals("editpass", edit("NAME=player\nMAIL=new@example.com", new SocketWrapper()));

        assertEquals("player@example.com", account.getMail());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void passwordChangeNeedsTheCurrentPassword() {
        assertEquals("editpass", edit("NAME=player\nMAIL=player@example.com\nCHNG=changed", loggedIn(account)));
        assertEquals("hash", account.getPass());

        assertEquals("edit", edit("NAME=player\nMAIL=player@example.com\nPASS=secret\nCHNG=changed", loggedIn(account)));
        assertEquals("changed-hash", account.getPass());
    }

    @Test
    void currentPasswordAllowsAnUpdateBeforeLogin() {
        assertEquals("edit", edit("NAME=player\nMAIL=new@example.com\nPASS=secret\nCHNG=secret", new SocketWrapper()));

        assertEquals("new@example.com", account.getMail());
        assertEquals("hash", account.getPass());
    }
}
