package eu.eahub.services.discord;

import eu.eahub.enums.GameGenre;
import eu.eahub.enums.SubscriptionType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordBotService {

    private final JDA jda;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    @Value("${services.bot-activity-enabled}")
    private boolean botActivityEnabled;

    @PostConstruct
    public void init() {
        if (!botActivityEnabled) {
            log.debug("Bot activity is disabled, skipping initialization");
            return;
        }
        jda.addEventListener(new ListenerAdapter() {
            @Override
            public void onReady(ReadyEvent event) {
                jda.updateCommands()
                        .addCommands(
                                Commands.slash("subscribe", "Subscribe this channel to updates for a specific game genre")
                                        .addOptions(getSubscriptionTypeOptions(), getGameGenreOptions()),
                                Commands.slash("unsubscribe", "Unsubscribe this server from updates for a specific game genre")
                                        .addOptions(getUnsubscribeTypeOptions(), getGameGenreOptions())
                        )
                        .queue();
            }
        });
    }

    private OptionData getSubscriptionTypeOptions() {
        return new OptionData(OptionType.STRING, "type", "Type of updates to subscribe to", true)
                .addChoices(
                        new Command.Choice("Server status (games, players). Should be used on its own channel", SubscriptionType.STATUS.getValue()),
                        new Command.Choice("Game logs (connections)", SubscriptionType.LOGS.getValue()),
                        new Command.Choice("Game scoreboards (end of round)", SubscriptionType.SCOREBOARD.getValue())
                );
    }

    private OptionData getUnsubscribeTypeOptions() {
        return new OptionData(OptionType.STRING, "type", "Type of updates to unsubscribe from", true)
                .addChoices(
                        new Command.Choice("Server status (games, players)", SubscriptionType.STATUS.getValue()),
                        new Command.Choice("Game logs (connections)", SubscriptionType.LOGS.getValue()),
                        new Command.Choice("Game scoreboards (end of round)", SubscriptionType.SCOREBOARD.getValue())
                );
    }

    private OptionData getGameGenreOptions() {
        return new OptionData(OptionType.STRING, "genre", "Game genre to subscribe to", true)
                .addChoices(
                        new Command.Choice("All genres (summary view)", GameGenre.ALL.getValue()),
                        new Command.Choice("Football (FIFA, UEFA)", GameGenre.FOOTBALL.getValue()),
                        new Command.Choice("Fighting (Fight Night)", GameGenre.FIGHTING.getValue()),
                        new Command.Choice("American Football (Madden, NCAA)", GameGenre.AMERICAN_FOOTBALL.getValue()),
                        new Command.Choice("Basketball (NBA Live)", GameGenre.BASKETBALL.getValue()),
                        new Command.Choice("Racing (Need for Speed)", GameGenre.RACING.getValue()),
                        new Command.Choice("Hockey (NHL)", GameGenre.HOCKEY.getValue()),
                        new Command.Choice("FPS (Medal of Honor)", GameGenre.FPS.getValue()),
                        new Command.Choice("Golf (Tiger Woods PGA)", GameGenre.GOLF.getValue())
                );
    }

    public void sendMessage(List<String> channelIds, String message) {
        if (!botActivityEnabled) {
            log.debug("Bot activity is disabled, skipping message: {}", message);
            return;
        }
        for (String channelId : channelIds) {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null && message != null && !message.isEmpty()) {
                Runnable sendTask = () -> channel.sendMessage(message).queue();
                scheduler.schedule(sendTask, 1, TimeUnit.SECONDS);
            }
        }
    }

    public void sendImages(List<String> channelIds, List<File> imageFiles, String message) {
        if (!botActivityEnabled) {
            log.debug("Bot activity is disabled, skipping images: {}", imageFiles.stream().map(File::getName).toList());
            return;
        }
        for (String channelId : channelIds) {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                Runnable sendTask = () -> {
                    try {
                        // Discord allows up to 10 files per message
                        int batchSize = 10;
                        for (int i = 0; i < imageFiles.size(); i += batchSize) {
                            List<File> batch = imageFiles.subList(i, Math.min(i + batchSize, imageFiles.size()));
                            List<FileUpload> uploads = batch.stream().map(FileUpload::fromData).toList();
                            if (message == null || message.isEmpty() || i > 0) {
                                channel.sendFiles(uploads).queue();
                            } else {
                                channel.sendMessage(message).addFiles(uploads).queue();
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error sending images to Discord channel {}", channelId, e);
                    }
                };
                scheduler.schedule(sendTask, 1, TimeUnit.SECONDS);
            }
        }
    }

    public void updateActivity(String activity) {
        if (!botActivityEnabled) {
            log.debug("Bot activity is disabled, skipping activity update: {}", activity);
            return;
        }

        if (jda != null) {
            jda.getPresence().setActivity(Activity.customStatus(activity));
        }
    }
}