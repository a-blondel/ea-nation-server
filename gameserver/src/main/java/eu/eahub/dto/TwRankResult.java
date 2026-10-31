package eu.eahub.dto;

import eu.eahub.entities.core.GameEntity;
import eu.eahub.enums.TwReceipt;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The outcome of a Tiger Woods match report: the receipt, and the match it was for when it was found
 */
@Getter
@AllArgsConstructor
public class TwRankResult {
    private TwReceipt receipt;
    private GameEntity game;
}
