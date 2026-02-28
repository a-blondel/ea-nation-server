package eu.eahub.services.discord;

import eu.eahub.entities.StatusMessageEntity;
import eu.eahub.enums.GameGenre;
import eu.eahub.enums.SubscriptionType;
import eu.eahub.repositories.DiscordStatusMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * Service responsible for managing Discord status messages.
 * Generates and updates status messages with current game information for each subscribed genre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusMessageService {

    private final DiscordStatusMessageRepository statusMessageRepository;
    private final StatusMessageContentService statusMessageContentService;
    private final ChannelSubscriptionService channelSubscriptionService;
    private final JDA jda;

    @Value("${services.bot-activity-enabled}")
    private boolean botActivityEnabled;

    /**
     * Scheduled method to update all status messages every 30 seconds.
     * Processes all game genres and updates their respective status messages.
     */
    @Scheduled(fixedDelay = 30000)
    public void updateStatusMessages() {
        if (!botActivityEnabled) {
            log.debug("Bot activity updates are disabled");
            return;
        }

        List<StatusMessageEntity> entries = statusMessageRepository.findAll();
        if (entries.isEmpty()) {
            log.debug("No status message subscriptions found");
            return;
        }

        // Use CountDownLatch to wait for all async Discord operations to complete
        CountDownLatch latch = new CountDownLatch(entries.size());
        List<StatusMessageEntity> updatedEntries = new CopyOnWriteArrayList<>();

        for (StatusMessageEntity entry : entries) {
            updateStatusMessageForEntry(entry, updatedEntries, latch);
        }

        try {
            // Wait for all Discord operations to complete
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for Discord message operations to complete", e);
        }

        // Save all updated entries after all Discord operations are done
        if (!updatedEntries.isEmpty()) {
            statusMessageRepository.saveAll(updatedEntries);
            log.debug("Saved {} updated status message entries", updatedEntries.size());
        }
    }

    /**
     * Update status message for a specific entry.
     */
    private void updateStatusMessageForEntry(StatusMessageEntity entry,
                                             List<StatusMessageEntity> updatedEntries,
                                             CountDownLatch latch) {
        TextChannel channel = jda.getTextChannelById(entry.getChannelId());
        if (channel == null) {
            log.warn("Channel {} not found for status message", entry.getChannelId());
            latch.countDown();
            return;
        }

        try {
            // Generate content for the specific game genre
            String content = generateStatusMessageContent(entry.getGameGenre());

            if (entry.getMessageId() == null) {
                // Send new message
                channel.sendMessage(content)
                        .queue(msg -> {
                            entry.setMessageId(msg.getId());
                            entry.setUpdatedAt(LocalDateTime.now());
                            updatedEntries.add(entry);
                            latch.countDown();
                            log.debug("Sent new status message for genre {} in channel {}",
                                    entry.getGameGenre(), entry.getChannelId());
                        }, error -> {
                            log.error("Failed to send status message for channel {} and genre {}",
                                    entry.getChannelId(), entry.getGameGenre(), error);
                            latch.countDown();
                        });
            } else {
                // Edit existing message
                channel.editMessageById(entry.getMessageId(), content)
                        .queue(success -> {
                            entry.setUpdatedAt(LocalDateTime.now());
                            updatedEntries.add(entry);
                            latch.countDown();
                            log.debug("Updated status message for genre {} in channel {}",
                                    entry.getGameGenre(), entry.getChannelId());
                        }, error -> {
                            // Check if message was deleted (error code 10008: Unknown Message)
                            if (error.getMessage() != null && error.getMessage().contains("10008")) {
                                log.info("Status message {} was deleted, removing subscriptions for channel {} and genre {}",
                                        entry.getMessageId(), entry.getChannelId(), entry.getGameGenre());
                                // Delete both the status message and channel subscription since the message was removed
                                try {
                                    statusMessageRepository.delete(entry);
                                    channelSubscriptionService.unsubscribe(entry.getGuildId(), SubscriptionType.STATUS, entry.getGameGenre());
                                    log.info("Successfully removed all subscriptions for guild {} and genre {}", entry.getGuildId(), entry.getGameGenre());
                                } catch (Exception deleteError) {
                                    log.error("Failed to delete subscriptions for guild {} and genre {}",
                                            entry.getGuildId(), entry.getGameGenre(), deleteError);
                                }
                            } else {
                                log.error("Failed to edit status message for channel {} and genre {}",
                                        entry.getChannelId(), entry.getGameGenre(), error);
                            }
                            latch.countDown();
                        });
            }
        } catch (Exception e) {
            log.error("Failed to process status message for channel {} and genre {}",
                    entry.getChannelId(), entry.getGameGenre(), e);
            latch.countDown();
        }
    }

    /**
     * Generate the content for a status message based on the game genre.
     */
    private String generateStatusMessageContent(GameGenre gameGenre) {
        try {
            String content = statusMessageContentService.generateStatusContent(gameGenre);

            // Add timestamp footer
            long unixTimestamp = Instant.now().getEpochSecond();
            content += "---\n*Last updated <t:" + unixTimestamp + ":R>*";

            return content;
        } catch (Exception e) {
            log.error("Failed to generate status content for genre {}", gameGenre, e);
            return "❌ **Error generating status for " + gameGenre.name() + "**\n\n" +
                    "Unable to retrieve current game information. Please try again later.\n\n" +
                    "*Last updated <t:" + Instant.now().getEpochSecond() + ":R>*";
        }
    }

    /**
     * Create or update a status message subscription for a guild and game genre.
     *
     * @param guildId   the Discord guild ID
     * @param channelId the Discord channel ID where messages should be sent
     * @param gameGenre the game genre to monitor
     * @return the created or updated StatusMessageEntity
     */
    @Transactional
    public StatusMessageEntity upsertStatusMessage(String guildId, String channelId, GameGenre gameGenre) {
        StatusMessageEntity entity = statusMessageRepository.findByGuildIdAndGameGenre(guildId, gameGenre)
                .orElse(new StatusMessageEntity());
        entity.setGuildId(guildId);
        entity.setChannelId(channelId);
        entity.setGameGenre(gameGenre);
        entity.setUpdatedAt(LocalDateTime.now());

        // Clear message ID if channel changed to force new message creation
        if (!channelId.equals(entity.getChannelId())) {
            entity.setMessageId(null);
        }

        StatusMessageEntity saved = statusMessageRepository.save(entity);
        log.info("Upserted status message subscription for guild {} genre {} in channel {}",
                guildId, gameGenre, channelId);
        return saved;
    }

    /**
     * Delete a status message subscription for a guild and game genre.
     *
     * @param guildId   the Discord guild ID
     * @param gameGenre the game genre to stop monitoring
     */
    @Transactional
    public void deleteStatusMessage(String guildId, GameGenre gameGenre) {
        statusMessageRepository.deleteByGuildIdAndGameGenre(guildId, gameGenre);
        log.info("Deleted status message subscription for guild {} genre {}", guildId, gameGenre);
    }
}
