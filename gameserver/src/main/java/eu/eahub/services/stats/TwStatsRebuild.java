package eu.eahub.services.stats;

import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.stats.TwEntryEntity;
import eu.eahub.entities.stats.TwEventEntity;
import eu.eahub.entities.stats.TwGameReportEntity;
import eu.eahub.entities.stats.TwPersonaStatsEntity;
import eu.eahub.repositories.stats.TwEntryRepository;
import eu.eahub.repositories.stats.TwEventRepository;
import eu.eahub.repositories.stats.TwGameReportRepository;
import eu.eahub.repositories.stats.TwPersonaStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static eu.eahub.services.server.GameServerService.ALL_TW;

/**
 * Rebuild the Tiger Woods statistics from the stored match reports and tournament scorecards, with the same rules as
 * when they were received (tw04.rebuild-stats=true), e.g. after importing results
 * Runs at startup, before the game servers accept connections
 */
@Slf4j
@RequiredArgsConstructor
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "tw04.rebuild-stats", havingValue = "true")
public class TwStatsRebuild implements CommandLineRunner {

    private final TwGameReportRepository twGameReportRepository;
    private final TwPersonaStatsRepository twPersonaStatsRepository;
    private final TwEntryRepository twEntryRepository;
    private final TwEventRepository twEventRepository;
    private final TwTournamentService twTournamentService;

    @Override
    @Transactional
    public void run(String... args) {
        for (String vers : ALL_TW) {
            rebuild(vers);
        }
        log.warn("Set tw04.rebuild-stats back to false, otherwise the statistics are rebuilt at every start");
    }

    /**
     * Replace a game's statistics with the ones replayed from its reports, cards and closed days
     *
     * @param vers the game version
     */
    @Transactional
    public void rebuild(String vers) {
        twPersonaStatsRepository.deleteAll(twPersonaStatsRepository.findByVers(vers));
        twPersonaStatsRepository.flush();
        Map<Long, TwPersonaStatsEntity> stats = new HashMap<>();

        // Matches, in the order they were played
        List<TwGameReportEntity> reports = twGameReportRepository.findByVers(vers);
        int matches = 0;
        for (int i = 0; i + 1 < reports.size(); i++) {
            TwGameReportEntity host = reports.get(i);
            TwGameReportEntity guest = reports.get(i + 1);
            GameEntity game = host.getGameConnection().getGame();
            if (!game.getId().equals(guest.getGameConnection().getGame().getId()) || host.getSide() != 0 || guest.getSide() != 1) {
                continue;
            }
            TwStatsService.applyMatch(TwStatsService.isMatchPlay(game), List.of(TwStatsService.card(host), TwStatsService.card(guest)),
                    new TwPersonaStatsEntity[]{
                            stats(stats, host.getGameConnection().getPersonaConnection().getPersona(), vers),
                            stats(stats, guest.getGameConnection().getPersonaConnection().getPersona(), vers)});
            matches++;
            i++;
        }

        // Tournament rounds: golf from each counted card, and each event (or weekend series) entered once
        List<TwEntryEntity> entries = twEntryRepository.findCounted(vers, 0, 65535);
        Set<String> entered = new HashSet<>();
        for (TwEntryEntity entry : entries) {
            TwEventEntity event = entry.getEvent();
            int first = event.getSeriesStart() == null ? event.getEventDay() : event.getSeriesStart();
            TwPersonaStatsEntity s = stats(stats, entry.getPersona(), vers);
            if (entered.add(entry.getPersona().getId() + ":" + first)) {
                s.setEvents(s.getEvents() + 1);
            }
            TwStatsService.addGolf(s, TwTournamentService.card(entry));
            // Settling the closed days again pays them from scratch
            entry.setFinalRank(null);
            entry.setPrize(null);
        }
        twEntryRepository.saveAll(entries);
        twPersonaStatsRepository.saveAll(new ArrayList<>(stats.values()));
        List<TwEventEntity> events = twEventRepository.findByVersAndEventDayBetweenOrderByEventDay(vers, 0, 65535);
        events.forEach(event -> event.setSettledOn(null));
        twEventRepository.saveAll(events);
        twTournamentService.settle(vers, twTournamentService.today());
        log.info("{} statistics rebuilt: {} matches, {} tournament rounds, {} players", vers, matches, entries.size(), stats.size());
    }

    private static TwPersonaStatsEntity stats(Map<Long, TwPersonaStatsEntity> stats, PersonaEntity persona, String vers) {
        return stats.computeIfAbsent(persona.getId(), id -> {
            TwPersonaStatsEntity s = new TwPersonaStatsEntity();
            s.setPersona(persona);
            s.setVers(vers);
            return s;
        });
    }
}
