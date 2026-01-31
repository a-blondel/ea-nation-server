package com.ea.steps;

import com.ea.dto.BuddySocketWrapper;
import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.services.core.*;
import com.ea.services.server.SocketManager;
import com.ea.services.social.BuddyService;
import com.ea.services.stats.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Socket;

@Slf4j
@RequiredArgsConstructor
@Component
public class SocketProcessor {
    private final AuthService authService;
    private final AccountService accountService;
    private final PersonaService personaService;
    private final StatsService statsService;
    private final GameService gameService;
    private final LeagueService leagueService;
    private final SocketWriter socketWriter;
    private final SocketManager socketManager;
    private final BuddyService buddyService;
    private final RoomService roomService;
    private final UserSetService userSetService;

    /**
     * Dispatch to appropriate service based on request type
     *
     * @param socket     the socket to handle
     * @param socketData the object to process
     */
    public void process(Socket socket, SocketData socketData) {
        SocketWrapper socketWrapper = socketManager.getSocketWrapperBySocket(socket);
        BuddySocketWrapper buddySocketWrapper = socketManager.getBuddySocketWrapperBySocket(socket);
        switch (socketData.getIdMessage()) {
            case ("~png"):
                break;
            case ("@tic"):
                socketWriter.write(socket, socketData);
                break;
            case ("@dir"):
                authService.dir(socket, socketData);
                break;
            case ("AUTH"):
                buddyService.auth(socket, socketData, buddySocketWrapper);
                break;
            case ("EPGT"):
                buddyService.epgt(socket, socketData);
                break;
            case ("RGET"):
                buddyService.rget(socket, socketData, buddySocketWrapper);
                break;
            case ("RADM"):
                buddyService.radm(socket, socketData, buddySocketWrapper);
                break;
            case ("RDEM"):
                buddyService.rdem(socket, socketData, buddySocketWrapper);
                break;
            case ("USCH"):
                buddyService.usch(socket, socketData);
                break;
            case ("RADD"):
                buddyService.radd(socket, socketData, buddySocketWrapper);
                break;
            case ("RDEL"):
                buddyService.rdel(socket, socketData, buddySocketWrapper);
                break;
            case ("RRSP"):
                buddyService.rrsp(socket, socketData, buddySocketWrapper);
                break;
            case ("PSET"):
                buddyService.pset(socket, socketData, buddySocketWrapper);
                break;
            case ("PADD"):
                buddyService.padd(socket, socketData, buddySocketWrapper);
                break;
            case ("PDEL"):
                buddyService.pdel(socket, socketData, buddySocketWrapper);
                break;
            case ("SEND"):
                buddyService.send(socket, socketData, buddySocketWrapper);
                break;
            case ("GINV"):
                buddyService.ginv(socket, socketData, buddySocketWrapper);
                break;
            case ("GRSP"):
                buddyService.grsp(socket, socketData, buddySocketWrapper);
                break;
            case ("GRVK"):
                buddyService.grvk(socket, socketData, buddySocketWrapper);
                break;
            case ("DISC"):
                buddyService.disc(buddySocketWrapper);
                break;
            case ("addr"):
                authService.addr(socket, socketData);
                break;
            case ("skey"):
                authService.skey(socket, socketData);
                break;
            case ("news"):
                authService.news(socket, socketData);
                break;
            case ("sele"):
                authService.sele(socket, socketData, socketWrapper);
                break;
            case ("priv"):
                authService.priv(socket, socketData);
                break;
            case ("qdef"):
                authService.qdef(socket, socketData);
                break;
            case ("slst"):
                authService.slst(socket, socketData);
                break;
            case ("uatr"):
                authService.uatr(socket, socketData);
                break;
            case ("user"):
                accountService.user(socket, socketData, socketWrapper);
                break;
            case ("acct"):
                accountService.acct(socket, socketData);
                break;
            case ("edit"):
                accountService.edit(socket, socketData);
                break;
            case ("auth"):
                accountService.auth(socket, socketData, socketWrapper);
                break;
            case ("lost"):
                accountService.lost(socket, socketData);
                break;
            case ("cper"):
                personaService.cper(socket, socketData, socketWrapper);
                break;
            case ("pers"):
                personaService.pers(socket, socketData, socketWrapper);
                break;
            case ("dper"):
                personaService.dper(socket, socketData, socketWrapper);
                break;
            case ("llvl"):
                personaService.llvl(socket, socketData, socketWrapper);
                break;
            case ("rept"):
                personaService.rept(socket, socketData, socketWrapper);
                break;
            case ("usld"):
                personaService.usld(socket, socketData);
                break;
            case ("ussv"):
                personaService.ussv(socket, socketData);
                break;
            case ("rcat"):
                roomService.rcat(socket, socketData, socketWrapper);
                break;
            case ("arom"):
                roomService.arom(socket, socketData, socketWrapper);
                break;
            case ("room"):
                roomService.room(socket, socketData);
                break;
            case ("move"):
                roomService.move(socket, socketData, socketWrapper);
                break;
            case ("mesg"):
                roomService.mesg(socket, socketData, socketWrapper);
                break;
            case ("cate"):
                statsService.cate(socket, socketData, socketWrapper);
                break;
            case ("snap"):
                statsService.snap(socket, socketData, socketWrapper);
                break;
            case ("rank"):
                statsService.rank(socket, socketData, socketWrapper);
                break;
            case ("gqwk"):
                gameService.gqwk(socket, socketData, socketWrapper);
                break;
            case ("gsea"):
                gameService.gsea(socket, socketData, socketWrapper);
                break;
            case ("gget"):
                gameService.gget(socket, socketData);
                break;
            case ("gjoi"):
                gameService.gjoi(socket, socketData, socketWrapper);
                break;
            case ("gpsc"):
                gameService.gpsc(socket, socketData, socketWrapper);
                break;
            case ("gcre"):
                gameService.gcre(socket, socketData, socketWrapper);
                break;
            case ("glea"):
                gameService.glea(socket, socketData, socketWrapper);
                break;
            case ("gpss"):
                gameService.gpss(socket, socketData);
                break;
            case ("gsta"):
                gameService.gsta(socket, socketData, socketWrapper);
                break;
            case ("gset"):
                gameService.gset(socket, socketData, socketWrapper);
                break;
            case ("gdel"):
                gameService.gdel(socket, socketData, socketWrapper);
                break;
            case ("filt"):
                gameService.filt(socket, socketData);
                break;
            case ("ilgs"):
                leagueService.ilgs(socket, socketData);
                break;
            case ("ilgf"):
                leagueService.ilgf(socket, socketData);
                break;
            case ("ilgt"):
                leagueService.ilgt(socket, socketData);
                break;
            case ("ilsc"):
                leagueService.ilsc(socket, socketData);
                break;
            case ("ilou"):
                leagueService.ilou(socket, socketData);
                break;
            case ("ucre"):
                userSetService.ucre(socket, socketData, socketWrapper);
                break;
            case ("usea"):
                userSetService.usea(socket, socketData, socketWrapper);
                break;
            case ("ujoi"):
                userSetService.ujoi(socket, socketData, socketWrapper);
                break;
            case ("ulea"):
                userSetService.ulea(socket, socketData, socketWrapper);
                break;
            case ("udel"):
                userSetService.udel(socket, socketData, socketWrapper);
                break;
            case ("uadm"):
                userSetService.uadm(socket, socketData, socketWrapper);
                break;
            case ("auxi"):
                userSetService.auxi(socket, socketData, socketWrapper);
                break;
            case ("onln"):
                userSetService.onln(socket, socketData, socketWrapper);
                break;
            default:
                log.info("Unsupported operation: {}", socketData.getIdMessage());
                socketWriter.write(socket, socketData);
                break;
        }
    }

}
