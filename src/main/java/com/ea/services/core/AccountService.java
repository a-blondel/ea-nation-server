package com.ea.services.core;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.core.AccountEntity;
import com.ea.entities.core.PersonaConnectionEntity;
import com.ea.mappers.SocketMapper;
import com.ea.repositories.core.AccountRepository;
import com.ea.repositories.core.BlacklistRepository;
import com.ea.steps.SocketWriter;
import com.ea.utils.AccountUtils;
import com.ea.utils.EmailUtils;
import com.ea.utils.PasswordUtils;
import com.ea.utils.SocketUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class AccountService {

    private final PasswordUtils passwordUtils;
    private final SocketMapper socketMapper;
    private final AccountRepository accountRepository;
    private final BlacklistRepository blacklistRepository;
    private final PersonaService personaService;
    private final SocketWriter socketWriter;
    private final EmailUtils emailUtils;

    /**
     * Account creation
     *
     * @param socket
     * @param socketData
     */
    public void acct(Socket socket, SocketData socketData) {
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");
        if (name == null) {
            // FIFA 10 uses 'MAIL' instead of 'NAME', while other games use 'NAME'
            // This is a huge problem as when the player tries to connect to another game, his name is null
            // We could set the name to the email address, but the max length is 16 characters for the name
            // So, we just return an error and make the player create an account in a game that uses 'NAME'
            socketData.setIdMessage("acctimst"); // Invalid error (EC_INV_MASTER)
            socketWriter.write(socket, socketData);
            return;
        }

        Optional<AccountEntity> accountEntityOpt = accountRepository.findByName(name);
        if (accountEntityOpt.isPresent()) {
            socketData.setIdMessage("acctdupl"); // Duplicate account error (EC_DUPLICATE)
            int alts = Integer.parseInt(getValueFromSocket(socketData.getInputMessage(), "ALTS"));
            if (alts > 0) {
                String opts = AccountUtils.suggestNames(alts, name);
                Map<String, String> content = Stream.of(new String[][]{
                        {"OPTS", opts}
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                socketData.setOutputData(content);
            }
        } else {
            AccountEntity accountEntity = socketMapper.toAccountEntity(socketData.getInputMessage());
            accountRepository.save(accountEntity);
        }
        socketWriter.write(socket, socketData);
    }

    /**
     * Account update
     *
     * @param socket
     * @param socketData
     */
    public void edit(Socket socket, SocketData socketData) {
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");

        Optional<AccountEntity> accountEntityOpt = accountRepository.findByName(name);
        if (accountEntityOpt.isPresent()) {
            AccountEntity accountEntity = accountEntityOpt.get();

            String pass = getValueFromSocket(socketData.getInputMessage(), "PASS");
            String mail = getValueFromSocket(socketData.getInputMessage(), "MAIL");
            String spam = getValueFromSocket(socketData.getInputMessage(), "SPAM");
            String chng = getValueFromSocket(socketData.getInputMessage(), "CHNG");

            boolean update = false;
            boolean error = false;

            if (mail != null && !mail.equals(accountEntity.getMail())) {
                accountEntity.setMail(mail);
                update = true;
            }

            if (!pass.equals(chng)) {
                if (passwordUtils.bCryptMatches(pass, accountEntity.getPass())) {
                    accountEntity.setPass(passwordUtils.bCryptEncode(chng));
                    update = true;
                } else {
                    socketData.setIdMessage("editpass"); // Invalid password error (EC_INV_PASS)
                    error = true;
                }
            }

            if (!error && (update || !spam.equals(accountEntity.getSpam()))) {
                accountEntity.setSpam(spam);
                accountEntity.setUpdatedOn(LocalDateTime.now());
                accountRepository.save(accountEntity);
            }

        } else {
            socketData.setIdMessage("editimst"); // Inexisting error (EC_INV_MASTER)
        }

        socketWriter.write(socket, socketData);
    }

    /**
     * Account login
     *
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void auth(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");
        String pass = getValueFromSocket(socketData.getInputMessage(), "PASS");
        String vers = getValueFromSocket(socketData.getInputMessage(), "VERS");
        String slus = getValueFromSocket(socketData.getInputMessage(), "SLUS");


        Optional<AccountEntity> accountEntityOpt;
        if (name == null) {
            // FIFA 10 uses 'MAIL' instead of 'NAME'
            String mail = getValueFromSocket(socketData.getInputMessage(), "MAIL");
            List<AccountEntity> accounts = accountRepository.findByMail(mail);
            if (accounts.isEmpty()) {
                socketData.setIdMessage("authimst"); // Inexisting error (EC_INV_MASTER)
                socketWriter.write(socket, socketData);
                return;
            } else {
                accountEntityOpt = accounts.stream()
                        .filter(a -> a.getMail().equalsIgnoreCase(mail)
                                && !a.isBanned())
                        .findFirst();
            }
        } else {
            if (name.contains("@")) {
                name = name.split("@")[0] + name.split("@")[1];
            }
            accountEntityOpt = accountRepository.findByName(name);
        }

        if (accountEntityOpt.isPresent()) {
            AccountEntity accountEntity = accountEntityOpt.get();

            if (blacklistRepository.existsByIp(socket.getInetAddress().getHostAddress())
                    || accountEntity.isBanned()) {
                socketData.setIdMessage("authblak"); // IP is blacklisted or account is banned (can also use authband)
                socketWriter.write(socket, socketData);
                return;
            }

            String decodedPass = passwordUtils.ssc2Decode(pass);
            if (passwordUtils.bCryptMatches(decodedPass, accountEntity.getPass())) {
                synchronized (this) {
                    socketWrapper.setAccountEntity(accountEntity);
                }

                String personas = accountEntity.getPersonas().stream()
                        .filter(p -> p.getDeletedOn() == null)
                        .map(p -> p.getPers())
                        .collect(Collectors.joining(","));
                Map<String, String> content = Stream.of(new String[][]{
                        {"NAME", accountEntity.getName()},
                        {"ADDR", socket.getInetAddress().getHostAddress()},
                        {"PERSONAS", personas},
                        {"LOC", accountEntity.getLoc()},
                        {"MAIL", accountEntity.getMail()},
                        {"SPAM", accountEntity.getSpam()}
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                socketData.setOutputData(content);

                if (null != socketWrapper.getPersonaConnectionEntity()) {
                    log.error("User wasn't properly disconnected");
                    personaService.endPersonaConnection(socketWrapper);
                }

                PersonaConnectionEntity personaConnectionEntity = new PersonaConnectionEntity();
                personaConnectionEntity.setAddress(SocketUtils.handleLocalhostIp(socket.getRemoteSocketAddress().toString()));
                personaConnectionEntity.setVers(vers);
                personaConnectionEntity.setSlus(slus);
                synchronized (this) {
                    socketWrapper.setPersonaConnectionEntity(personaConnectionEntity);
                }

            } else {
                socketData.setIdMessage("authpass"); // Invalid password error (EC_INV_PASS)
            }
        } else {
            socketData.setIdMessage("authimst"); // Inexisting error (EC_INV_MASTER)
        }

        socketWriter.write(socket, socketData);
    }


    /**
     * Lost username or password
     * If we receive 'MAIL', then we have to send the username(s) matching the email address (if present)
     * If we receive 'NAME', then we have to send a new password to the email address linked to the account name (if present)
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void lost(Socket socket, SocketData socketData) {
        String mail = getValueFromSocket(socketData.getInputMessage(), "MAIL");
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");

        if (mail != null) {
            List<AccountEntity> accountEntities = accountRepository.findByMail(mail);
            if (!accountEntities.isEmpty()) {
                String htmlContent = emailUtils.getHtmlTemplate("lost_name.html")
                        .replace("${banner}", emailUtils.getBanner())
                        .replace("${accountNames}", accountEntities.stream().map(AccountEntity::getName).collect(Collectors.joining(" - ")));
                emailUtils.sendEmail("EA Nation - Lost name", htmlContent, mail);
            } else {
                socketData.setIdMessage("lostuusr"); // No account exists with the given email address (EC_UNKNOWN_USER)
            }
        } else if (name != null) {
            Optional<AccountEntity> accountEntityOpt = accountRepository.findByName(name);
            if (accountEntityOpt.isPresent()) {
                AccountEntity accountEntity = accountEntityOpt.get();
                String pass = passwordUtils.generateRandomPassword();
                accountEntity.setPass(passwordUtils.bCryptEncode(pass));
                accountEntity.setUpdatedOn(LocalDateTime.now());
                accountRepository.save(accountEntity);

                String htmlContent = emailUtils.getHtmlTemplate("lost_password.html")
                        .replace("${banner}", emailUtils.getBanner())
                        .replace("${name}", name)
                        .replace("${pass}", pass);
                emailUtils.sendEmail("EA Nation - Lost password", htmlContent, accountEntity.getMail());
            } else {
                socketData.setIdMessage("lostneml"); // No email address is available for the given master account (EC_NO_EMAIL)
            }
        }
        socketWriter.write(socket, socketData);
    }

}
