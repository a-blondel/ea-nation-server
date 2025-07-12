package com.ea.services.stats;


import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.core.GameConnectionEntity;
import com.ea.entities.core.GameEntity;
import com.ea.entities.core.PersonaEntity;
import com.ea.entities.stats.MohhGameReportEntity;
import com.ea.entities.stats.MohhPersonaStatsEntity;
import com.ea.enums.Mohh2Map;
import com.ea.enums.MohhMap;
import com.ea.mappers.SocketMapper;
import com.ea.repositories.core.GameConnectionRepository;
import com.ea.repositories.stats.MohhGameReportRepository;
import com.ea.repositories.stats.MohhPersonaStatsRepository;
import com.ea.services.server.GameServerService;
import com.ea.steps.SocketWriter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.enums.MohhRankCategory.*;
import static com.ea.services.server.GameServerService.*;
import static com.ea.utils.SocketUtils.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class MohhStatsService {

    private final SocketMapper socketMapper;
    private final SocketWriter socketWriter;
    private final MohhPersonaStatsRepository mohhPersonaStatsRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final MohhGameReportRepository mohhGameReportRepository;
    private final GameServerService gameServerService;

    /**
     * Retrieve ranking categories
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    public void cate(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"CC", "6"}, // <total # of categories in this view>
                {"IC", "6"}, // <total # of indices in this view>
                {"VC", "6"}, // <total # of variations in this view>
                {"U", "6"},
                {"SYMS", "6"},
                {"SS", "6"},
                {"R", String.join(",", Collections.nCopies(66, "1"))}, // <comma-separated-list of category,index,view data>
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
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
        String cols = getValueFromSocket(socketData.getInputMessage(), "COLS"); // send column information or not
        String start = getValueFromSocket(socketData.getInputMessage(), "START"); // <start ranking> (index)
        String categoryIndex = getValueFromSocket(socketData.getInputMessage(), "CI"); // <category-index>

        List<String> relatedVers = gameServerService.getRelatedVers(socketWrapper.getPersonaConnectionEntity().getVers());
        boolean isMohh = relatedVers.equals(MOH07_OR_UHS);
        String rankingCategory = getRankingCategory(isMohh, categoryIndex).mohh2Id;

        String columnNumber = isMohh ? "21" : "18";
        if (WEAPON_LEADERS.mohh2Id.equals(rankingCategory)) {
            columnNumber = "32";
        }

        // Must be fetched here to know the actual size
        List<MohhPersonaStatsEntity> mohhPersonaStatsEntityList = new ArrayList<>();
        long offset = 0;
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        if (MY_LEADERBOARD.mohh2Id.equals(rankingCategory)) {
            mohhPersonaStatsEntityList = mohhPersonaStatsRepository.getLeaderboardByVers(vers, 100, offset);
        } else if (TOP_100.mohh2Id.equals(rankingCategory)) {
            Long rank = mohhPersonaStatsRepository.getRankByPersonaIdAndVers(socketWrapper.getPersonaEntity().getId(), vers);
            offset = (rank != null) ? rank : 0;
            offset = Math.max(offset - 50, 0);
            mohhPersonaStatsEntityList = mohhPersonaStatsRepository.getLeaderboardByVers(vers, 100, offset);
        } else if (WEAPON_LEADERS.mohh2Id.equals(rankingCategory)) {
            mohhPersonaStatsEntityList = mohhPersonaStatsRepository.getWeaponLeaderboardByVers(vers, 100, offset);
        }

        Map<String, String> content = Stream.of(new String[][]{
                {"CHAN", chan}, // <matching request value>
                {"START", start}, // <actual start used>
                {"RANGE", String.valueOf(mohhPersonaStatsEntityList.size())}, // <actual range used>
                {"SEQN", seqn}, // <value provided in request>
                {"CC", columnNumber}, // <number of columns>
                {"FC", "1"}, // <number of fixed columns>
                {"DESC", ""}, // <list-description>
                {"PARAMS", "1,1,1,1,1,1,1,1,1,1,1,1"}, // <comma-separated list of integer parameters>
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        if ("1".equals(cols) && Set.of(MY_LEADERBOARD.mohh2Id, TOP_100.mohh2Id).contains(rankingCategory)) {
            content.putAll(Stream.of(new String[][]{
                    {"CN0", "RNK"}, // <column-name>
                    {"CD0", "\"Leaderboard Ranking\""}, // <column-name> (selected)
                    //{ "CP0", "1" }, // <column-parameter>
                    //{ "CW0", "50" }, // <column-width>
                    //{ "CT0", "1" }, // <column-type>
                    //{ "CS0", "1" }, // <column-style>
                    {"CN1", "Persona"},
                    {"CD1", "Persona"},
                    {"CN2", "Score"},
                    {"CD2", "Score"},
                    {"CN3", "Kills"},
                    {"CD3", "\"Total Kills\""},
                    {"CN4", "Deaths"},
                    {"CD4", "\"Total Deaths\""},
                    {"CN5", isMohh ? "Time" : "Accuracy"},
                    {"CD5", isMohh ? "\"Total Time Played Online\"" : "\"Accuracy %\""},
                    {"CN6", isMohh ? "Accuracy" : "Time"},
                    {"CD6", isMohh ? "\"Accuracy %\"" : "\"Total Time Played Online\""},
                    {"CN7", "KPM"},
                    {"CD7", "\"Kills Per Minute\""},
                    {"CN8", "DPM"},
                    {"CD8", "\"Deaths Per Minute\""},
                    {"CN9", isMohh ? "\"Fav. Map\"" : "Headshots"},
                    {"CD9", isMohh ? "\"Most Played Map\"" : "\"Total Headshots\""},
                    {"CN10", isMohh ? "\"Fav. Mode\"" : "\"Fav. Map\""},
                    {"CD10", isMohh ? "\"Most Played Mode\"" : "\"Most Played Map\""},
                    {"CN11", isMohh ? "\"Fav. Team\"" : "\"Fav. Mode\""},
                    {"CD11", isMohh ? "\"Most Played Team\"" : "\"Most Played Mode\""},
                    {"CN12", isMohh ? "Headshots" : "\"Fav. Team\""},
                    {"CD12", isMohh ? "\"Total Headshots\"" : "\"Most Played Team\""},
                    {"CN13", "Wins"},
                    {"CD13", "\"Total Wins\""},
                    {"CN14", "Losses"},
                    {"CD14", "\"Total Losses\""},
                    {"CN15", "\"DM RND\""},
                    {"CD15", "\"Deathmatch Rounds Played\""},
                    {"CN16", "\"INF RND\""},
                    {"CD16", "\"Infiltration Rounds Played\""},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            if (isMohh) {
                content.putAll(Stream.of(new String[][]{
                        {"CN17", "\"DOM RND\""},
                        {"CD17", "\"Domination Rounds Played\""},
                        {"CN18", "\"DEM RND\""},
                        {"CD18", "\"Demolition Rounds Played\""},
                        {"CN19", "\"HTL RND\""},
                        {"CD19", "\"Hold the Line Rounds Played\""},
                        {"CN20", "\"BL RND\""},
                        {"CD20", "\"Battle Lines Rounds Played\""},
                }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            } else {
                content.putAll(Stream.of(new String[][]{
                        {"CN17", "\"TDM RND\""},
                        {"CD17", "\"Team Deathmatch Rounds Played\""},
                }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            }
        } else if ("1".equals(cols) && WEAPON_LEADERS.mohh2Id.equals(rankingCategory)) {
            content.putAll(Stream.of(new String[][]{
                    {"CN0", "RNK"},
                    {"CD0", "\"Leaderboard Ranking\""},
                    {"CN1", "Persona"},
                    {"CD1", "Persona"},
                    {"CN2", "Kills"},
                    {"CD2", "\"Total Kills\""},
                    {"CN3", "Accuracy"},
                    {"CD3", "\"Accuracy %\""},
                    {"CN4", "\".45 Kill\""},
                    {"CD4", "\"M1911 Pistol Kills\""},
                    {"CN5", "\".45 Acc\""},
                    {"CD5", "\"M1911 Pistol Accuracy\""},
                    {"CN6", "\"THMP Kill\""},
                    {"CD6", "\"Thompson Kills\""},
                    {"CN7", "\"THMP Acc\""},
                    {"CD7", "\"Thompson Accuracy\""},
                    {"CN8", "\"BAR Kill\""},
                    {"CD8", "\"M1918 BAR Kills\""},
                    {"CN9", "\"BAR Acc\""},
                    {"CD9", "\"M1918 BAR Accuracy\""},
                    {"CN10", "\"GAR Kill\""},
                    {"CD10", "\"M1 Garand Kills\""},
                    {"CN11", "\"GAR Acc\""},
                    {"CD11", "\"M1 Garand Accuracy\""},
                    {"CN12", "\"SPFD Kill\""},
                    {"CD12", "\"Springfield Kills\""},
                    {"CN13", "\"SPFD Acc\""},
                    {"CD13", "\"Springfield Accuracy\""},
                    {"CN14", "\"SHOT Kill\""},
                    {"CD14", "\"M12 Shotgun Kills\""},
                    {"CN15", "\"SHOT Acc\""},
                    {"CD15", "\"M12 Shotgun Accuracy\""},
                    {"CN16", "\"BAZ Kill\""},
                    {"CD16", "\"M1A1 Bazooka Kills\""},
                    {"CN17", "\"BAZ Acc\""},
                    {"CD17", "\"M1A1 Bazooka Accuracy\""},
                    {"CN18", "\"P08 Kill\""},
                    {"CD18", "\"P08 Luger Kills\""},
                    {"CN19", "\"P08 Acc\""},
                    {"CD19", "\"P08 Luger Accuracy\""},
                    {"CN20", "\"MP40 Kill\""},
                    {"CD20", "\"MP40 Kills\""},
                    {"CN21", "\"MP40 Acc\""},
                    {"CD21", "\"MP40 Accuracy\""},
                    {"CN22", "\"StG44 Kill\""},
                    {"CD22", "\"StG44 AR Kills\""},
                    {"CN23", "\"StG44 Acc\""},
                    {"CD23", "\"StG44 AR Accuracy\""},
                    {"CN24", "\"KAR Kill\""},
                    {"CD24", "\"Karabiner 98K Kills\""},
                    {"CN25", "\"KAR Acc\""},
                    {"CD25", "\"Karabiner 98K Accuracy\""},
                    {"CN26", "\"GEWR Kill\""},
                    {"CD26", "\"Gewehr Kills\""},
                    {"CN27", "\"GEWR Acc\""},
                    {"CD27", "\"Gewehr Accuracy\""},
                    {"CN28", "\"PANZ Kill\""},
                    {"CD28", "\"Panzerschreck Kills\""},
                    {"CN29", "\"PANZ Acc\""},
                    {"CD29", "\"Panzerschreck Accuracy\""},
                    {"CN30", "\"GRND Kill\""},
                    {"CD30", "\"Grenade Kills\""},
                    {"CN31", "\"Melee Kill\""},
                    {"CD31", "\"Melee Kills\""},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
        }

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        snp(socket, isMohh, rankingCategory, mohhPersonaStatsEntityList, offset);
    }

    /**
     * Send ranking snapshot
     * Favorite team : 0 = Axis, 1 = Allied
     *
     * @param socket                     The socket to write the response to
     * @param isMohh                     If the game is MoHH or MoHH2
     * @param rankCategory               The rank category (e.g., MY_LEADERBOARD, TOP_100)
     * @param mohhPersonaStatsEntityList The list of persona stats entities
     * @param offset                     The offset for the ranking
     */
    public void snp(Socket socket, boolean isMohh, String rankCategory, List<MohhPersonaStatsEntity> mohhPersonaStatsEntityList, long offset) {
        List<Map<String, String>> rankingList = new ArrayList<>();
        for (MohhPersonaStatsEntity mohhPersonaStatsEntity : mohhPersonaStatsEntityList) {
            String name = mohhPersonaStatsEntity.getPersona().getPers();
            String rank = String.valueOf(++offset);
            String points = String.valueOf(mohhPersonaStatsEntity.getKill() - mohhPersonaStatsEntity.getDeath());
            if (Set.of(MY_LEADERBOARD.mohh2Id, TOP_100.mohh2Id).contains(rankCategory)) {
                String mostPlayedTeam = mohhPersonaStatsEntity.getAxis() > mohhPersonaStatsEntity.getAllies() ? "0" : "1";
                String playTime = String.valueOf(mohhPersonaStatsEntity.getPlayTime());
                String column6 = isMohh ? playTime : getPrecision(mohhPersonaStatsEntity.getHit(), mohhPersonaStatsEntity.getShot()); // mohh playtime, mohh2 accuracy
                String column7 = isMohh ? getPrecision(mohhPersonaStatsEntity.getHit(), mohhPersonaStatsEntity.getShot()) : playTime; // mohh accuracy, mohh2 playtime
                String column10 = isMohh ? getMostPlayedMap(mohhPersonaStatsEntity, isMohh) : String.valueOf(mohhPersonaStatsEntity.getHead()); // mohh map, mohh2 headshots
                String column11 = isMohh ? getMostPlayedMode(mohhPersonaStatsEntity) : getMostPlayedMap(mohhPersonaStatsEntity, isMohh); // mohh mode, mohh2 map
                String column12 = isMohh ? mostPlayedTeam : getMostPlayedMode(mohhPersonaStatsEntity); // mohh team, mohh2 mode
                String column13 = isMohh ? String.valueOf(mohhPersonaStatsEntity.getHead()) : mostPlayedTeam; // mohh headshots, mohh2 team
                String stats = String.join(",", // <stats>
                        String.valueOf(offset),
                        mohhPersonaStatsEntity.getPersona().getPers(),
                        String.valueOf(mohhPersonaStatsEntity.getKill() - mohhPersonaStatsEntity.getDeath()),
                        String.valueOf(mohhPersonaStatsEntity.getKill()),
                        String.valueOf(mohhPersonaStatsEntity.getDeath()),
                        column6,
                        column7,
                        playTime.equals("0") ? "0" : String.valueOf(new Formatter(Locale.US).format("%.3f", mohhPersonaStatsEntity.getKill() / (mohhPersonaStatsEntity.getPlayTime() / 60f))),
                        playTime.equals("0") ? "0" : String.valueOf(new Formatter(Locale.US).format("%.3f", mohhPersonaStatsEntity.getDeath() / (mohhPersonaStatsEntity.getPlayTime() / 60f))),
                        column10,
                        column11,
                        column12,
                        column13,
                        String.valueOf(mohhPersonaStatsEntity.getWin()),
                        String.valueOf(mohhPersonaStatsEntity.getLoss()),
                        String.valueOf(mohhPersonaStatsEntity.getDmRnd()),
                        String.valueOf(mohhPersonaStatsEntity.getCtfAllies() + mohhPersonaStatsEntity.getCtfAxis()) // CTF = Infiltration
                );

                if (isMohh) {
                    stats += "," + (mohhPersonaStatsEntity.getCapAllies() + mohhPersonaStatsEntity.getCapAxis()); // CAP = Domination
                    stats += "," + (mohhPersonaStatsEntity.getDemAllies() + mohhPersonaStatsEntity.getDemAxis());
                    stats += "," + (mohhPersonaStatsEntity.getKohAllies() + mohhPersonaStatsEntity.getKohAxis()); // KOH = Hold the Line
                    stats += "," + (mohhPersonaStatsEntity.getBlAllies() + mohhPersonaStatsEntity.getBlAxis());
                } else {
                    stats += "," + (mohhPersonaStatsEntity.getTdmAllies() + mohhPersonaStatsEntity.getTdmAxis());
                }
                rankingList.add(Stream.of(new String[][]{
                        {"N", name}, // <persona name>
                        {"R", rank}, // <rank>
                        {"P", points}, // <points>
                        {"O", "0"}, // <online> ?
                        {"S", stats}, // <stats>
                }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            } else if (WEAPON_LEADERS.mohh2Id.equals(rankCategory)) {
                rankingList.add(Stream.of(new String[][]{
                        {"N", name},
                        {"R", rank},
                        {"P", points},
                        {"O", "0"},
                        {"S", String.join(",",
                                String.valueOf(offset),
                                mohhPersonaStatsEntity.getPersona().getPers(),
                                String.valueOf(mohhPersonaStatsEntity.getKill()),
                                getPrecision(mohhPersonaStatsEntity.getHit(), mohhPersonaStatsEntity.getShot()),
                                String.valueOf(mohhPersonaStatsEntity.getColtKill()),
                                getPrecision(mohhPersonaStatsEntity.getColtHit(), mohhPersonaStatsEntity.getColtShot()),
                                String.valueOf(mohhPersonaStatsEntity.getTomKill()),
                                getPrecision(mohhPersonaStatsEntity.getTomHit(), mohhPersonaStatsEntity.getTomShot()),
                                String.valueOf(mohhPersonaStatsEntity.getBarKill()),
                                getPrecision(mohhPersonaStatsEntity.getBarHit(), mohhPersonaStatsEntity.getBarShot()),
                                String.valueOf(mohhPersonaStatsEntity.getGarKill()),
                                getPrecision(mohhPersonaStatsEntity.getGarHit(), mohhPersonaStatsEntity.getGarShot()),
                                String.valueOf(mohhPersonaStatsEntity.getEnfieldKill()),
                                getPrecision(mohhPersonaStatsEntity.getEnfieldHit(), mohhPersonaStatsEntity.getEnfieldShot()),
                                String.valueOf(mohhPersonaStatsEntity.getShottyKill()),
                                getPrecision(mohhPersonaStatsEntity.getShottyHit(), mohhPersonaStatsEntity.getShottyShot()),
                                String.valueOf(mohhPersonaStatsEntity.getBazKill()),
                                getPrecision(mohhPersonaStatsEntity.getBazHit(), mohhPersonaStatsEntity.getBazShot()),
                                String.valueOf(mohhPersonaStatsEntity.getLugerKill()),
                                getPrecision(mohhPersonaStatsEntity.getLugerHit(), mohhPersonaStatsEntity.getLugerShot()),
                                String.valueOf(mohhPersonaStatsEntity.getMp40Kill()),
                                getPrecision(mohhPersonaStatsEntity.getMp40Hit(), mohhPersonaStatsEntity.getMp40Shot()),
                                String.valueOf(mohhPersonaStatsEntity.getMp44Kill()),
                                getPrecision(mohhPersonaStatsEntity.getMp44Hit(), mohhPersonaStatsEntity.getMp44Shot()),
                                String.valueOf(mohhPersonaStatsEntity.getKarKill()),
                                getPrecision(mohhPersonaStatsEntity.getKarHit(), mohhPersonaStatsEntity.getKarShot()),
                                String.valueOf(mohhPersonaStatsEntity.getGewrKill()),
                                getPrecision(mohhPersonaStatsEntity.getGewrHit(), mohhPersonaStatsEntity.getGewrShot()),
                                String.valueOf(mohhPersonaStatsEntity.getPanzKill()),
                                getPrecision(mohhPersonaStatsEntity.getPanzHit(), mohhPersonaStatsEntity.getPanzShot()),
                                String.valueOf(mohhPersonaStatsEntity.getGrenKill()),
                                String.valueOf(mohhPersonaStatsEntity.getMeleeKill())
                        )
                        },
                }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            }
        }

        for (Map<String, String> ranking : rankingList) {
            SocketData socketData = new SocketData("+snp", null, ranking);
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Send ranking results.
     *
     * @param socket     The socket to write the response to
     * @param socketData The socket data
     */
    @Transactional
    public void rank(Socket socket, SocketData socketData) {
        String playerName = getValueFromSocket(socketData.getInputMessage(), "REPT", TAB_CHAR);
        String startTime = getValueFromSocket(socketData.getInputMessage(), "WHEN", TAB_CHAR);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATETIME_FORMAT);
        LocalDateTime parsedStartTime = LocalDateTime.parse(startTime, formatter);
        List<GameConnectionEntity> gameConnectionEntities = gameConnectionRepository.findMatchingGameConnections(playerName, parsedStartTime);
        if (!gameConnectionEntities.isEmpty()) {
            GameConnectionEntity gameConnectionEntity = gameConnectionEntities.get(0);
            MohhGameReportEntity mohhGameReportEntity = new MohhGameReportEntity();
            mohhGameReportEntity.setGameConnection(gameConnectionEntity);
            socketMapper.toMohhGameReportEntity(mohhGameReportEntity, socketData.getInputMessage());
            mohhGameReportRepository.save(mohhGameReportEntity);

            // Update PersonaStats with the new game report (ranked only)
            if (mohhGameReportEntity.getRnk() == 1) {
                MohhPersonaStatsEntity mohhPersonaStatsEntity = mohhPersonaStatsRepository.findByPersonaIdAndVersIn(
                        gameConnectionEntity.getPersonaConnection().getPersona().getId(), gameServerService.getRelatedVers(gameConnectionEntity.getGame().getVers()));
                if (mohhPersonaStatsEntity != null) {
                    updatePersonaStats(mohhPersonaStatsEntity, mohhGameReportEntity);
                    mohhPersonaStatsRepository.save(mohhPersonaStatsEntity);
                }
            }

            // This is to make sure the end time is set in case something goes wrong in 'gset'
            LocalDateTime endTime = LocalDateTime.now();
            new Thread(
                    () -> {
                        try {
                            Thread.sleep(5000);
                            gameConnectionEntity.setEndTime(endTime);
                            gameConnectionRepository.save(gameConnectionEntity);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
            );
        }
        socketWriter.write(socket, socketData);
    }

    /**
     * Update PersonaStats with the new game report
     *
     * @param mohhPersonaStatsEntity The PersonaStats entity to update
     * @param mohhGameReportEntity   The GameReport entity containing new stats
     */
    private void updatePersonaStats(MohhPersonaStatsEntity mohhPersonaStatsEntity, MohhGameReportEntity mohhGameReportEntity) {
        Field[] personaFields = MohhPersonaStatsEntity.class.getDeclaredFields();
        Set<String> gameReportFieldNames = Arrays.stream(MohhGameReportEntity.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        for (Field field : personaFields) {
            if (gameReportFieldNames.contains(field.getName()) && !"id".equalsIgnoreCase(field.getName())) {
                field.setAccessible(true);
                try {
                    Field gameReportField = MohhGameReportEntity.class.getDeclaredField(field.getName());
                    gameReportField.setAccessible(true);
                    if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                        field.set(mohhPersonaStatsEntity, (int) field.get(mohhPersonaStatsEntity) + (int) gameReportField.get(mohhGameReportEntity));
                    } else if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
                        field.set(mohhPersonaStatsEntity, (long) field.get(mohhPersonaStatsEntity) + (long) gameReportField.get(mohhGameReportEntity));
                    }
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    log.error("Error updating field: {}", field.getName(), e);
                }
            }
        }
    }

    /**
     * Get precision
     *
     * @param hit  number of hits
     * @param shot number of shots
     * @return precision in percentage
     */
    private String getPrecision(long hit, long shot) {
        long miss = shot - hit;
        String precision = "100";
        if (0 != hit + miss) {
            precision = String.valueOf(new Formatter(Locale.US).format("%.2f", hit / ((float) hit + miss) * 100));
        }
        return precision;
    }

    /**
     * Get most played map
     *
     * @param mohhPersonaStatsEntity entity containing map play counts
     * @param isMohh                 if the game is MoHH or MoHH2
     * @return most played map
     */
    private String getMostPlayedMap(MohhPersonaStatsEntity mohhPersonaStatsEntity, boolean isMohh) {
        Map<String, Integer> mapPlayCounts = new HashMap<>();
        for (int i = 1; i <= 28; i++) {
            try {
                Method method = MohhPersonaStatsEntity.class.getMethod("getMap" + i);
                int playCount = (int) method.invoke(mohhPersonaStatsEntity);

                // Find the corresponding enum by matching the id attribute
                String mapKey;
                if (isMohh) {
                    mapKey = "181";
                    for (MohhMap map : MohhMap.values()) {
                        if (map.code.equals("MAP" + i)) {
                            mapKey = map.decimalId;
                            break;
                        }
                    }
                    mapPlayCounts.put(mapKey, mapPlayCounts.getOrDefault(mapKey, 0) + playCount);
                } else {
                    mapKey = "101";
                    for (Mohh2Map map : Mohh2Map.values()) {
                        if (map.code.equals("MAP" + i)) {
                            mapKey = map.decimalId;
                            break;
                        }
                    }
                    mapPlayCounts.put(mapKey, mapPlayCounts.getOrDefault(mapKey, 0) + playCount);
                }
                mapPlayCounts.put(mapKey, mapPlayCounts.getOrDefault(mapKey, 0) + playCount);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                log.error("Error while getting map play count", e);
            }
        }

        // Determine the most played map
        String mostPlayedMapKey = Collections.max(mapPlayCounts.entrySet(), Map.Entry.comparingByValue()).getKey();

        // Map the most played map key to its corresponding ID
        if (isMohh) {
            return MohhMap.getMapStatId(mostPlayedMapKey);
        } else {
            return Mohh2Map.getMapStatId(mostPlayedMapKey);
        }
    }

    /**
     * Get most played mode
     * Values : 0 = DM, 1 = TDM, 2 = INF, 3 = DEM, 4 = DOM, 5 = HTL, 6 = BL
     * Note that on MoHH value 1 = "FS_M_TDM", surely TDM was planned but cut off
     *
     * @param mohhPersonaStatsEntity entity containing mode play counts
     * @return most played mode
     */
    private String getMostPlayedMode(MohhPersonaStatsEntity mohhPersonaStatsEntity) {
        int totalTdm = mohhPersonaStatsEntity.getTdmAllies() + mohhPersonaStatsEntity.getTdmAxis();
        int totalInf = mohhPersonaStatsEntity.getCtfAllies() + mohhPersonaStatsEntity.getCtfAxis();
        int totalDem = mohhPersonaStatsEntity.getDemAllies() + mohhPersonaStatsEntity.getDemAxis();
        int totalDom = mohhPersonaStatsEntity.getCapAllies() + mohhPersonaStatsEntity.getCapAxis();
        int totalHtl = mohhPersonaStatsEntity.getKohAllies() + mohhPersonaStatsEntity.getKohAxis();
        int totalBl = mohhPersonaStatsEntity.getBlAllies() + mohhPersonaStatsEntity.getBlAxis();

        Map<String, Integer> modeCounts = new HashMap<>();
        modeCounts.put("0", mohhPersonaStatsEntity.getDmRnd());
        modeCounts.put("1", totalTdm);
        modeCounts.put("2", totalInf);
        modeCounts.put("3", totalDem);
        modeCounts.put("4", totalDom);
        modeCounts.put("5", totalHtl);
        modeCounts.put("6", totalBl);

        return Collections.max(modeCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    /**
     * Check if the game matches the given criteria for gsea packet
     *
     * @param gameEntity The game entity to check
     * @param paramsMap  The parameters map containing the criteria
     * @param vers       The version of the game
     * @return true if the game matches the criteria, false otherwise
     */
    public boolean matchesCriteria(GameEntity gameEntity, Map<String, String> paramsMap, String vers) {
        String name = paramsMap.get("NAME");
        String available = paramsMap.getOrDefault("AVAILABLE", paramsMap.get("AVAIL")); // Game not full (AVAIL on MoHH2)
        String mode = paramsMap.get("MODE");
        String map = paramsMap.get("MAP");
        String ff = paramsMap.get("FF"); // Friendly fire
        String aim = paramsMap.get("AIM"); // Aim assist
        String ctrl = paramsMap.get("CTRL"); // Elite or Wii Zapper
        String maxsize = paramsMap.get("MAXSIZE");
        String sysmaskParam = paramsMap.get("SYSMASK");
        String sysflagsParam = paramsMap.get("SYSFLAGS");
        String tb = paramsMap.get("TB"); // Team balance
        String ak = paramsMap.get("AK"); // Auto-kick
        String smg = paramsMap.get("SMG");
        String hmg = paramsMap.get("HMG");
        String rif = paramsMap.get("RIF");
        String snip = paramsMap.get("SNIP");
        String shotg = paramsMap.get("SHOTG");
        String baz = paramsMap.get("BAZ");
        String gren = paramsMap.get("GREN");

        String nameDb = gameEntity.getName().replaceAll("\"", "").toLowerCase();
        boolean availableDb = gameEntity.getGameConnections().stream().filter(connection -> null == connection.getEndTime()).count() < gameEntity.getMaxsize();
        String[] params = gameEntity.getParams().split(",");
        String modeDb = params[0];
        int mapDb = Integer.parseInt(params[1], 16); // The map in db is in hex, so we need to convert it to decimal
        boolean hasPwDb = gameEntity.getPass() != null;
        String ffDb = params[2];

        String aimDb = null, ctrlDb = null, tbDb = null, akDb = null;
        switch (vers) {
            case PSP_MOH_07 -> aimDb = params[3];
            case PSP_MOH_08 -> {
                aimDb = params[3];
                tbDb = params[4];
                akDb = params[9];
            }
            case WII_MOH_08 -> {
                tbDb = params[3];
                akDb = params[8];
                ctrlDb = params[9];
            }
        }

        boolean isRankedDb = vers.equals(PSP_MOH_07) ? StringUtils.isNotEmpty(params[8]) : StringUtils.isNotEmpty(params[17]);

        String smgDb = null, hmgDb = null, rifDb = null, snipDb = null, shotgDb = null, bazDb = null, grenDb = null;
        if (vers.equals(PSP_MOH_08) || vers.equals(WII_MOH_08)) {
            smgDb = params[10];
            hmgDb = params[11];
            rifDb = params[12];
            snipDb = params[13];
            shotgDb = params[14];
            bazDb = params[15];
            grenDb = params[16];
        }

        boolean matchesFlags = matchesFlags(sysmaskParam, sysflagsParam, hasPwDb, isRankedDb);

        return (name == null || nameDb.contains(name.replaceAll("\"", "").toLowerCase()))
                && (available.equals("-1") || availableDb)
                && (mode.equals("-1") || modeDb.equals(mode))
                && (map.equals("-1") || mapDb == Integer.parseInt(map))
                && (ff.equals("-1") || (ff.equals("0") && ffDb.equals("")) || ffDb.equals(ff))
                && (aim == null || aim.equals("-1") || (aim.equals("0") && aimDb.equals("")) || aimDb.equals(aim))
                && (ctrl == null || ctrl.equals("-1") || (ctrl.equals("0") && ctrlDb.equals("")) || ctrlDb.equals(ctrl))
                && (maxsize == null || gameEntity.getMaxsize() == Integer.parseInt(maxsize))
                && matchesFlags
                && (tb == null || tb.equals("-1") || (tb.equals("0") && tbDb.equals("")) || tbDb.equals(tb))
                && (ak == null || ak.equals("-1") || (ak.equals("0") && akDb.equals("")) || akDb.equals(ak))
                && (smg == null || smg.equals("-1") || (smg.equals("0") && smgDb.equals("")) || smgDb.equals(smg))
                && (hmg == null || hmg.equals("-1") || (hmg.equals("0") && hmgDb.equals("")) || hmgDb.equals(hmg))
                && (rif == null || rif.equals("-1") || (rif.equals("0") && rifDb.equals("")) || rifDb.equals(rif))
                && (snip == null || snip.equals("-1") || (snip.equals("0") && snipDb.equals("")) || snipDb.equals(snip))
                && (shotg == null || shotg.equals("-1") || (shotg.equals("0") && shotgDb.equals("")) || shotgDb.equals(shotg))
                && (baz == null || baz.equals("-1") || (baz.equals("0") && bazDb.equals("")) || bazDb.equals(baz))
                && (gren == null || gren.equals("-1") || (gren.equals("0") && grenDb.equals("")) || grenDb.equals(gren));
    }


    /**
     * Matches the flags based on the system mask and system flags parameters.
     *
     * @param sysmaskParam  The system mask parameter from the client
     * @param sysflagsParam The system flags parameter from the client
     * @param hasPwDb       Indicates if the game has a password
     * @param isRankedDb    Indicates if the game is ranked
     * @return true if the game matches the flags, false otherwise
     */
    private boolean matchesFlags(String sysmaskParam, String sysflagsParam, boolean hasPwDb, boolean isRankedDb) {
        if (sysmaskParam == null) {
            return true;
        }

        int clientMask = Integer.parseInt(sysmaskParam);
        int clientFlags = sysflagsParam != null ? Integer.parseInt(sysflagsParam) : 0;

        // Calculate game's flags based on ranked and password status
        int gameFlags = 0;
        if (hasPwDb) {
            gameFlags |= (1 << 16);  // Set bit 16 for password
        }
        if (isRankedDb) {
            gameFlags |= (1 << 18);  // Set bit 18 for ranked
        }

        // Only check the bits that the client cares about (specified in mask)
        return (gameFlags & clientMask) == (clientFlags & clientMask);
    }

    /**
     * Get stats and rank for a persona
     *
     * @param personaEntity The persona entity to get stats for
     * @param vers          The version of the game
     * @return A map containing stats and rank
     */
    public Map<String, String> getStatsAndRank(PersonaEntity personaEntity, String vers) {
        Map<String, String> result = new HashMap<>();

        MohhPersonaStatsEntity mohhPersonaStatsEntity = mohhPersonaStatsRepository.findByPersonaIdAndVers(personaEntity.getId(), vers);
        boolean hasStats = null != mohhPersonaStatsEntity;

        String stats = ",,,,,,," +
                (hasStats ? Long.toHexString(mohhPersonaStatsEntity.getKill()) : "0") +
                "," +
                (hasStats ? Long.toHexString(mohhPersonaStatsEntity.getDeath()) : "0");

        String rank = hasStats ? String.valueOf(mohhPersonaStatsRepository.getRankByPersonaIdAndVers(mohhPersonaStatsEntity.getPersona().getId(), vers)) : "";

        result.put("stats", stats);
        result.put("rank", rank);
        return result;
    }


    /**
     * Initialize persona stats for a new persona
     *
     * @param personaEntity The persona entity to initialize stats for
     * @param vers          The version of the game
     * @param slus          The SLUS code for the game
     */
    public void initPersonaStats(PersonaEntity personaEntity, String vers, String slus) {
        MohhPersonaStatsEntity mohhPersonaStatsEntity = new MohhPersonaStatsEntity();
        mohhPersonaStatsEntity.setPersona(personaEntity);
        mohhPersonaStatsEntity.setVers(vers);
        mohhPersonaStatsEntity.setSlus(slus);
        mohhPersonaStatsRepository.save(mohhPersonaStatsEntity);
    }

}
