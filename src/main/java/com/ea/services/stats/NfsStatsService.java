package com.ea.services.stats;


import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.mappers.SocketMapper;
import com.ea.repositories.core.GameConnectionRepository;
import com.ea.services.server.GameServerService;
import com.ea.steps.SocketWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.enums.LeaderboardLabel.*;
import static com.ea.services.server.GameServerService.*;
import static com.ea.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class NfsStatsService {

    private final SocketMapper socketMapper;
    private final SocketWriter socketWriter;
    private final GameConnectionRepository gameConnectionRepository;
    private final GameServerService gameServerService;

    private static int getRaceCount(String vers) {
        int raceCount;
        if (PSP_NFS_06.equals(vers)) {
            raceCount = 11; // NFS Most Wanted (10 + 1 for some reason)
        } else if (PSP_NFS_07.equals(vers)) {
            raceCount = 30; // NFS Carbon
        } else if (PSP_NFS_08.equals(vers)) {
            raceCount = 38; // NFS ProStreet
        } else if (PSP_NFS_09.equals(vers)) {
            raceCount = 36; // NFS Undercover
        } else {
            raceCount = 0;
        }
        return raceCount;
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

        Stream<String> beginLabels = PSP_NFS_06.equals(vers) ? Stream.of() : Stream.of(MY_LEADERBOARD.name, TOP_100.name, LAP_RECORDS.name);
        Stream<String> endLabels = Stream.of(FORWARD.name, REVERSE.name);

        // Dynamic generation of SYMS
        int firstRaceId = PSP_NFS_06.equals(vers) ? 0 : 1;
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
        int startIndex = PSP_NFS_06.equals(vers) ? 0 : 3; // Index of "Race 1" in SYMS
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
                {"IC", String.valueOf(PSP_NFS_06.equals(vers) ? raceCount + 1 : raceCount)}, // Indices count (number of races)
                {"VC", String.valueOf(PSP_NFS_06.equals(vers) ? (raceCount + 1) * 2 : raceCount * 2)}, // Variation count (each race has Forward + Reverse)
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
        String categoryIndex = getValueFromSocket(socketData.getInputMessage(), "CI"); // 1 = My Leaderboard, 2 = TOP 100, 3 = Lap records

        String columnNumber = Integer.parseInt(categoryIndex) > 2 ? "4" : "10";

        Map<String, String> content = Stream.of(new String[][]{
                {"CHAN", chan}, // <matching request value>
                {"START", start}, // <actual start used>
                {"RANGE", "0"}, // <actual range used>
                {"SEQN", seqn}, // <value provided in request>
                {"CC", columnNumber}, // <number of columns>
                {"FC", "1"}, // <number of fixed columns>
                {"DESC", ""}, // <list-description>
                {"PARAMS", "1,1,1,1,1,1,1,1,1,1,1,1"}, // <comma-separated list of integer parameters>
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        if ("1".equals(cols) && Integer.parseInt(categoryIndex) > 2) {
            content.putAll(Stream.of(new String[][]{
                    {"CN0", "RNK"},
                    {"CN1", "Persona"},
                    {"CN2", "\"Fastest lap time\""},
                    {"CN3", "Car"},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
        } else if ("1".equals(cols)) {
            content.putAll(Stream.of(new String[][]{
                    {"CN0", "RNK"},
                    {"CD0", "\"Leaderboard Ranking\""},
                    {"CN1", "Persona"},
                    {"CD1", "Persona"},
                    {"CN2", "Score"},
                    {"CD2", "Score"},
                    {"CN4", "Wins"},
                    {"CD4", "Wins"},
                    {"CN5", "Losses"},
                    {"CD5", "Losses"},
                    {"CN8", "DNF%"},
                    {"CD8", "\"Did not finish percentage\""},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
        }

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        //snp(socket, isMohh, rankingCategory, mohhPersonaStatsEntityList, offset);
    }

//    /**
//     * Send ranking snapshot
//     * Favorite team : 0 = Axis, 1 = Allied
//     *
//     * @param socket                     The socket to write the response to
//     * @param rankCategory               The rank category (e.g., MY_LEADERBOARD, TOP_100)
//     * @param nfsPersonaStatsEntityList The list of persona stats entities
//     * @param offset                     The offset for the ranking
//     */
//    public void snp(Socket socket, String rankCategory, List<NfsPersonaStatsEntity> nfsPersonaStatsEntityList, long offset) {
//        List<Map<String, String>> rankingList = new ArrayList<>();
//
//        for (Map<String, String> ranking : rankingList) {
//            SocketData socketData = new SocketData("+snp", null, ranking);
//            socketWriter.write(socket, socketData);
//        }
//    }

}
