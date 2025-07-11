package com.ea.mappers;

import com.ea.entities.core.AccountEntity;
import com.ea.entities.core.GameEntity;
import com.ea.entities.stats.MohhGameReportEntity;
import com.ea.utils.PasswordUtils;
import com.ea.utils.SocketUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static com.ea.utils.SocketUtils.RETURN_CHAR;
import static com.ea.utils.SocketUtils.TAB_CHAR;

@Slf4j
@Component
public class SocketMapper {

    @Autowired
    private PasswordUtils passwordUtils;

    public GameEntity toGameEntity(String socket, String vers, String slus) {
        GameEntity gameEntity = new GameEntity();
        gameEntity.setVers(vers);
        gameEntity.setSlus(slus);
        setFieldsFromSocket(gameEntity, socket, RETURN_CHAR);
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

    public MohhGameReportEntity toMohhGameReportEntity(MohhGameReportEntity mohhGameReportEntity, String socket) {
        setFieldsFromSocket(mohhGameReportEntity, socket, TAB_CHAR);
        aggregateMohhGameReportFields(mohhGameReportEntity);
        return mohhGameReportEntity;
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