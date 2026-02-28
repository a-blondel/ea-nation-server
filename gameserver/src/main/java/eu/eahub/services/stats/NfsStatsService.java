package eu.eahub.services.stats;


import eu.eahub.dto.SocketData;
import eu.eahub.dto.SocketWrapper;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.stats.NfsGameReportEntity;
import eu.eahub.entities.stats.NfsPersonaStatsEntity;
import eu.eahub.mappers.SocketMapper;
import eu.eahub.repositories.core.GameConnectionRepository;
import eu.eahub.repositories.stats.NfsGameReportRepository;
import eu.eahub.repositories.stats.NfsPersonaStatsRepository;
import eu.eahub.services.server.GameServerService;
import eu.eahub.steps.SocketWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.eahub.enums.LeaderboardLabel.*;
import static eu.eahub.services.server.GameServerService.*;
import static eu.eahub.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class NfsStatsService {

    private static final int[] PSP_NFS_06_VENUE_MAP_FORWARD = {1, 2, 4, 6, 8, 10, 12, 14, 16, 18};
    private static final int[] PSP_NFS_06_VENUE_MAP_REVERSE = {1, 3, 5, 7, 9, 11, 13, 15, 17, 19};
    private final SocketMapper socketMapper;
    private final SocketWriter socketWriter;
    private final GameConnectionRepository gameConnectionRepository;
    private final GameServerService gameServerService;
    private final NfsPersonaStatsRepository nfsPersonaStatsRepository;
    private final NfsGameReportRepository nfsGameReportRepository;

    private static int getRaceCount(String vers) {
        int raceCount;
        if (PSP_NFS06.equals(vers)) {
            raceCount = 11; // NFS Most Wanted (10 + 1 for some reason)
        } else if (PSP_NFS07.equals(vers)) {
            raceCount = 30; // NFS Carbon
        } else if (PSP_NFS08.equals(vers)) {
            raceCount = 38; // NFS ProStreet
        } else if (PSP_NFS09.equals(vers)) {
            raceCount = 36; // NFS Undercover
        } else {
            raceCount = 0;
        }
        return raceCount;
    }

    private static String getStats(boolean hasStats, NfsPersonaStatsEntity nfsPersonaStatsEntity, String vers) {
        long totalGames = hasStats ? (nfsPersonaStatsEntity.getWins() + nfsPersonaStatsEntity.getLosses()) : 0;
        long dnf = totalGames > 0 ? Math.round(((double) nfsPersonaStatsEntity.getDidquit() + nfsPersonaStatsEntity.getDiddisc()) / totalGames * 100) : 0;
        dnf = Math.min(dnf, 100); // Ensure DNF percentage does not exceed 100

        String stats;
        if (PSP_NFS06.equals(vers)) {
            // For NFS MW: wins at index 2, losses at index 3, DNF% at index 10
            stats = ",," +
                    (hasStats ? Long.toHexString(nfsPersonaStatsEntity.getWins()) : "0") +
                    "," +
                    (hasStats ? Long.toHexString(nfsPersonaStatsEntity.getLosses()) : "0") +
                    ",,,," +
                    (hasStats ? Long.toHexString(dnf) : "0");
        } else {
            // For other NFS games: wins at index 1, losses at index 2, DNF% at index 4
            stats = "," +
                    (hasStats ? Long.toHexString(nfsPersonaStatsEntity.getWins()) : "0") +
                    "," +
                    (hasStats ? Long.toHexString(nfsPersonaStatsEntity.getLosses()) : "0") +
                    ",," +
                    (hasStats ? Long.toHexString(dnf) : "0") +
                    ",,,,";
        }
        return stats;
    }

    /**
     * Utility method to wrap SYMS values with quotes for proper formatting
     *
     * @param syms List of symbol names
     * @return Comma-separated string with each value wrapped in quotes
     */
    private String formatSymsWithQuotes(List<String> syms) {
        return syms.stream()
                .map(sym -> "\"" + sym + "\"")
                .collect(Collectors.joining(","));
    }

    /**
     * Retrieve ranking categories
     *
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void cate(SocketData socketData, SocketWrapper socketWrapper) {
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        int raceCount = getRaceCount(vers);

        Stream<String> beginLabels = PSP_NFS06.equals(vers) ? Stream.of() : Stream.of(MY_LEADERBOARD.name, TOP_100.name, LAP_RECORDS.name);
        Stream<String> endLabels = Stream.of(FORWARD.name, REVERSE.name);

        // Dynamic generation of SYMS
        int firstRaceId = PSP_NFS06.equals(vers) ? 0 : 1;
        List<String> syms = Stream.concat(
                Stream.concat(beginLabels,
                        // Dynamic generation of "Race X"
                        Stream.iterate(firstRaceId, i -> i <= raceCount, i -> i + 1)
                                .map(i -> "Race " + i)),
                endLabels
        ).toList();

        String categoryNames = formatSymsWithQuotes(syms);

        // Dynamic generation of R
        StringBuilder rDataBuilder = new StringBuilder();

        // Fixed categories
        rDataBuilder.append("0,1,1,1,1,1,1,1,")
                .append("1,1,1,1,1,1,1,1,")
                .append("2,1,1,1,1,1,1,1");

        // Dynamic race categories (one per race with Forward/Reverse variations)
        int startIndex = PSP_NFS06.equals(vers) ? 0 : 3; // Index of "Race 1" in SYMS
        int forwardIndex = syms.size() - 2; // Index of "Forward" in SYMS
        int reverseIndex = syms.size() - 1; // Index of "Reverse" in SYMS
        for (int i = startIndex; i < startIndex + raceCount; i++) {
            rDataBuilder.append(",")
                    .append(i).append(",2,") // Race ID matching SYMS index, 2 variations (Forward + Reverse)
                    .append(forwardIndex).append(",1,1,1,1,1,")  // Forward (SYMS)
                    .append(reverseIndex).append(",1,1,1,1,1");  // Reverse (SYMS)
        }

        Map<String, String> content = Stream.of(new String[][]{
                {"CC", String.valueOf(raceCount + 3)}, // Categories count (fixed + number of races)
                {"IC", String.valueOf(PSP_NFS06.equals(vers) ? raceCount + 1 : raceCount)}, // Indices count (number of races)
                {"VC", String.valueOf(PSP_NFS06.equals(vers) ? (raceCount + 1) * 2 : raceCount * 2)}, // Variation count (each race has Forward + Reverse)
                {"SYMS", categoryNames},
                {"SS", String.valueOf(categoryNames.length() + 1)}, // Total character count in SYMS + 1
                {"R", rDataBuilder.toString()},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
    }

    /**
     * Request ranking snapshot
     *
     * @param socket        The socket to write the response to
     * @param socketData    The socket data
     * @param socketWrapper The socket wrapper of current connection
     */
    public void snap(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String chan = getValueFromSocket(socketData.getInputMessage(), "CHAN");
        String seqn = getValueFromSocket(socketData.getInputMessage(), "SEQN");
        String cols = getValueFromSocket(socketData.getInputMessage(), "COLS");
        String start = getValueFromSocket(socketData.getInputMessage(), "START");
        String categoryIndex = getValueFromSocket(socketData.getInputMessage(), "CI"); // 0 = My Leaderboard, 1 = TOP 100, 3+ = Lap records
        String itemIndex = getValueFromSocket(socketData.getInputMessage(), "II"); // For lap records: 0 = Forward, 1 = Reverse

        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        int ci = Integer.parseInt(categoryIndex);

        // Fetch data based on category type
        List<NfsPersonaStatsEntity> nfsPersonaStatsEntityList = new ArrayList<>();
        List<NfsGameReportEntity> nfsGameReportEntityList = new ArrayList<>();
        long offset = 0;
        boolean isLapRecords = ci >= 3;

        if (ci == 0) { // My Leaderboard
            Long rank = nfsPersonaStatsRepository.getRankByPersonaIdAndVers(socketWrapper.getPersonaEntity().getId(), vers);
            offset = (rank != null) ? Math.max(rank - 50, 0) : 0;
            nfsPersonaStatsEntityList = nfsPersonaStatsRepository.getLeaderboardByVers(vers, 100, offset);
        } else if (ci == 1) { // Top 100
            nfsPersonaStatsEntityList = nfsPersonaStatsRepository.getLeaderboardByVers(vers, 100, offset);
        } else if (isLapRecords) { // Lap Records (CI >= 3)
            int dir = Integer.parseInt(itemIndex != null ? itemIndex : "0"); // II=0 -> DIR=0, II=1 -> DIR=1

            int venue = ci - 2; // CI=3 -> VENUE=1, CI=4 -> VENUE=2, etc.
            if (PSP_NFS06.equals(vers)) {
                // MW uses one venue for forward and another one for reverse
                if (dir == 0) {
                    venue = PSP_NFS_06_VENUE_MAP_FORWARD[ci - 3];
                } else if (dir == 1) {
                    venue = PSP_NFS_06_VENUE_MAP_REVERSE[ci - 3];
                }
            }

            if (PSP_NFS06.equals(vers)) {
                // For NFS Most Wanted, use racetime instead of lap
                nfsGameReportEntityList = nfsGameReportRepository.getRacetimeRecordsByVenueAndDir(vers, venue, dir, 100, offset);
            } else {
                // For other NFS games, use lap time
                nfsGameReportEntityList = nfsGameReportRepository.getLapRecordsByVenueAndDir(vers, venue, dir, 100, offset);
            }
        }

        String columnNumber = isLapRecords ? "4" : "6";
        int actualSize = isLapRecords ? nfsGameReportEntityList.size() : nfsPersonaStatsEntityList.size();

        Map<String, String> content = Stream.of(new String[][]{
                {"CHAN", chan}, // <matching request value>
                {"START", start}, // <actual start used>
                {"RANGE", String.valueOf(actualSize)}, // <actual range used>
                {"SEQN", seqn}, // <value provided in request>
                {"CC", columnNumber}, // <number of columns>
                {"FC", "1"}, // <number of fixed columns>
                {"DESC", ""}, // <list-description>
                {"PARAMS", "1,1,1,1,1,1,1,1,1,1,1,1"}, // <comma-separated list of integer parameters>
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        if ("1".equals(cols) && isLapRecords) {
            if (PSP_NFS06.equals(vers)) {
                content.putAll(Stream.of(new String[][]{
                        {"CN0", "RNK"},
                        {"CN1", "Persona"},
                        {"CN2", "Car"},
                        {"CN3", "\"Lap time\""},
                }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            } else {
                content.putAll(Stream.of(new String[][]{
                        {"CN0", "RNK"},
                        {"CN1", "Persona"},
                        {"CN2", "\"Fastest lap time\""},
                        {"CN3", "Car"},
                }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            }
        } else if ("1".equals(cols)) {
            content.putAll(Stream.of(new String[][]{
                    {"CN0", "RNK"},
                    {"CD0", "\"Leaderboard Ranking\""},
                    {"CN1", "Persona"},
                    {"CD1", "Persona"},
                    {"CN2", "Score"},
                    {"CD2", "Score"},
                    {"CN3", "Wins"},
                    {"CD3", "Wins"},
                    {"CN4", "Losses"},
                    {"CD4", "Losses"},
                    {"CN5", "DNF%"},
                    {"CD5", "\"Did not finish percentage\""},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
        }

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        // Send ranking data
        if (isLapRecords) {
            snpLapRecords(socket, nfsGameReportEntityList, offset + 1, vers);
        } else {
            snp(socket, nfsPersonaStatsEntityList, offset + 1, vers);
        }
    }

    /**
     * Send ranking snapshot for persona stats (My Leaderboard and Top 100)
     *
     * @param socket                    The socket to write the response to
     * @param nfsPersonaStatsEntityList The list of persona stats entities
     * @param startRank                 The starting rank for display
     * @param vers                      The game version
     */
    public void snp(Socket socket, List<NfsPersonaStatsEntity> nfsPersonaStatsEntityList, long startRank, String vers) {
        List<Map<String, String>> rankingList = new ArrayList<>();
        long rank = startRank;

        for (NfsPersonaStatsEntity nfsPersonaStatsEntity : nfsPersonaStatsEntityList) {
            String name = nfsPersonaStatsEntity.getPersona().getPers();
            String points = String.valueOf(nfsPersonaStatsEntity.getWins() - nfsPersonaStatsEntity.getLosses());

            long totalGames = nfsPersonaStatsEntity.getWins() + nfsPersonaStatsEntity.getLosses();
            long dnfCount = nfsPersonaStatsEntity.getDidquit() + nfsPersonaStatsEntity.getDiddisc();
            String dnfPercentage = totalGames > 0 ? String.format("%.0f", (dnfCount * 100.0) / totalGames) : "0";

            String stats = String.join(",",
                    String.valueOf(rank),
                    name,
                    points, // Score (wins - losses)
                    String.valueOf(nfsPersonaStatsEntity.getWins()),
                    String.valueOf(nfsPersonaStatsEntity.getLosses()),
                    dnfPercentage
            );

            rankingList.add(Stream.of(new String[][]{
                    {"N", name}, // <persona name>
                    {"R", String.valueOf(rank)}, // <rank>
                    {"P", points}, // <points>
                    {"O", "0"}, // <online>
                    {"S", stats}, // <stats>
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));

            rank++;
        }

        for (Map<String, String> ranking : rankingList) {
            SocketData socketData = new SocketData("+snp", null, ranking);
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Send ranking snapshot for lap records
     *
     * @param socket                  The socket to write the response to
     * @param nfsGameReportEntityList The list of game report entities
     * @param startRank               The starting rank for display
     * @param vers                    The game version
     */
    public void snpLapRecords(Socket socket, List<NfsGameReportEntity> nfsGameReportEntityList, long startRank, String vers) {
        List<Map<String, String>> rankingList = new ArrayList<>();
        long rank = startRank;

        for (NfsGameReportEntity nfsGameReportEntity : nfsGameReportEntityList) {
            String name = nfsGameReportEntity.getGameConnection().getPersonaConnection().getPersona().getPers();

            // Use racetime for NFS MW, lap time for other games
            long timeValue = PSP_NFS06.equals(vers) ? nfsGameReportEntity.getRacetime() : nfsGameReportEntity.getLap();
            String timeString = String.valueOf(timeValue);

            String stats;
            if (PSP_NFS06.equals(vers)) {
                stats = String.join(",",
                        String.valueOf(rank),
                        name,
                        String.valueOf(nfsGameReportEntity.getCar()),
                        timeString
                );
            } else {
                stats = String.join(",",
                        String.valueOf(rank),
                        name,
                        timeString,
                        String.valueOf(nfsGameReportEntity.getCar())
                );
            }

            rankingList.add(Stream.of(new String[][]{
                    {"N", name}, // <persona name>
                    {"R", String.valueOf(rank)}, // <rank>
//                    {"P", timeString}, // <time value>
//                    {"O", "0"}, // <online>
                    {"S", stats}, // <stats>
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));

            rank++;
        }

        for (Map<String, String> ranking : rankingList) {
            SocketData socketData = new SocketData("+snp", null, ranking);
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Get persona stats and rank for NFS games
     *
     * @param personaEntity The persona entity to get stats for
     * @param vers          The version of the game
     * @return A map containing stats and rank
     */
    public Map<String, String> getStatsAndRank(PersonaEntity personaEntity, String vers) {
        Map<String, String> result = new HashMap<>();

        NfsPersonaStatsEntity nfsPersonaStatsEntity = nfsPersonaStatsRepository.findByPersonaIdAndVers(personaEntity.getId(), vers);
        boolean hasStats = null != nfsPersonaStatsEntity;

        String stats = getStats(hasStats, nfsPersonaStatsEntity, vers);

        String rank = hasStats ? String.valueOf(nfsPersonaStatsRepository.getRankByPersonaIdAndVers(nfsPersonaStatsEntity.getPersona().getId(), vers)) : "";

        result.put("stats", stats);
        result.put("rank", rank);
        return result;
    }

}
