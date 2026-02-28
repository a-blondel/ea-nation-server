package eu.eahub.mappers;

import eu.eahub.entities.core.AccountEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.stats.MohhGameReportEntity;
import eu.eahub.utils.PasswordUtils;
import eu.eahub.utils.SocketUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static eu.eahub.utils.SocketUtils.RETURN_CHAR;
import static eu.eahub.utils.SocketUtils.TAB_CHAR;

@Slf4j
@Component
public class SocketMapper {

    @Autowired
    private PasswordUtils passwordUtils;

    public GameEntity toGameEntity(String socket, String vers) {
        GameEntity gameEntity = new GameEntity();
        gameEntity.setVers(vers);
        setFieldsFromSocket(gameEntity, socket, RETURN_CHAR);

        // gpsc packets comes without a room, it doesn't matter as this entity is not saved to the database,
        // we redirect the gpsc packet to the host which uses gcre with a room this time that is saved to the database
        int roomId = 0;

        // The game sends "ROOM" but the entity uses "roomId" for naming convention, so we handle it manually
        String roomIdStr = SocketUtils.getValueFromSocket(socket, "ROOM", RETURN_CHAR);
        if (roomIdStr != null && !roomIdStr.isEmpty()) {
            try {
                roomId = Integer.parseInt(roomIdStr);
            } catch (NumberFormatException e) {
                log.error("Invalid room ID format: {}", roomIdStr, e);
            }
        }
        gameEntity.setRoomId(roomId);
        gameEntity.setStartTime(LocalDateTime.now());
        return gameEntity;
    }

    public AccountEntity toAccountEntity(String socket) {
        AccountEntity accountEntity = new AccountEntity();
        setFieldsFromSocket(accountEntity, socket, RETURN_CHAR);
        accountEntity.setPass(passwordUtils.bCryptEncode(passwordUtils.ssc2Decode(accountEntity.getPass())));
        accountEntity.setCreatedOn(LocalDateTime.now());
        return accountEntity;
    }

    public void toMohhGameReportEntity(MohhGameReportEntity mohhGameReportEntity, String socket) {
        setFieldsFromSocket(mohhGameReportEntity, socket, TAB_CHAR);
        aggregateMohhGameReportFields(mohhGameReportEntity);
    }

    private void setFieldsFromSocket(Object entity, String socket, String splitter) {
        Field[] fields = entity.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            String value = SocketUtils.getValueFromSocket(socket, field.getName().toUpperCase(), splitter);
            if (value != null) {
                try {
                    if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                        field.set(entity, Integer.parseInt(value));
                    } else if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
                        field.set(entity, Long.parseLong(value));
                    } else {
                        field.set(entity, value);
                    }
                } catch (IllegalAccessException e) {
                    log.error("Error while setting fields from socket", e);
                }
            }
        }
    }

    private void aggregateMohhGameReportFields(MohhGameReportEntity mohhGameReportEntity) {
        int totalHit = 0;
        int totalShot = 0;
        int totalHead = 0;

        Field[] fields = mohhGameReportEntity.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                    int value = (int) field.get(mohhGameReportEntity);
                    if (field.getName().endsWith("Hit")) {
                        totalHit += value;
                    } else if (field.getName().endsWith("Shot")) {
                        totalShot += value;
                    } else if (field.getName().endsWith("Head")) {
                        totalHead += value;
                    }
                }
            } catch (IllegalAccessException e) {
                log.error("Error while aggregating game report fields", e);
            }
        }

        mohhGameReportEntity.setShot(totalShot);
        mohhGameReportEntity.setHit(totalHit);
        mohhGameReportEntity.setHead(totalHead);
    }
}