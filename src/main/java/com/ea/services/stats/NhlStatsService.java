package com.ea.services.stats;

import com.ea.entities.core.PersonaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class NhlStatsService {

    /**
     * Get stats and rank for a persona
     *
     * @param personaEntity The persona entity to get stats for
     * @param vers          The version of the game
     * @return A map containing stats and rank
     */
    public Map<String, String> getStatsAndRank(PersonaEntity personaEntity, String vers) {
        Map<String, String> result = new HashMap<>();
        result.put("stats", ",A,5,,14,,,,"); // Hex values. 2nd value is wins, 3rd is losses, 5ths is DNF %
        result.put("rank", "1234");
        return result;
    }

}
