package eu.eahub.listeners;

import eu.eahub.enums.GameGenre;
import eu.eahub.enums.SubscriptionType;
import eu.eahub.repositories.ParamRepository;
import eu.eahub.services.discord.ChannelSubscriptionService;
import eu.eahub.services.discord.DiscordBotService;
import eu.eahub.services.discord.StatusMessageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import static net.dv8tion.jda.api.Permission.MANAGE_SERVER;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChannelSubscriptionListener extends ListenerAdapter {
    private final JDA jda;
    private final ChannelSubscriptionService subscriptionService;
    private final DiscordBotService discordBotService;
    private final ParamRepository paramRepository;
    private final StatusMessageService statusMessageService;

    @PostConstruct
    public void register() {
        jda.addEventListener(this);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        switch (command) {
            case "subscribe" -> handleSubscription(event, true);
            case "unsubscribe" -> handleSubscription(event, false);
            default -> {
            }
        }
    }

    private void handleSubscription(SlashCommandInteractionEvent event, boolean subscribe) {
        String type = event.getOption("type").getAsString();
        String genreValue = event.getOption("genre").getAsString();

        SubscriptionType subscriptionType;
        GameGenre gameGenre;

        try {
            subscriptionType = SubscriptionType.fromValue(type);
            gameGenre = GameGenre.fromValue(genreValue);
        } catch (IllegalArgumentException e) {
            event.reply("Invalid subscription type or game genre.").setEphemeral(true).queue();
            return;
        }

        // Validate that "All genres" is only available for STATUS subscription type
        if (gameGenre == GameGenre.ALL && subscriptionType != SubscriptionType.STATUS) {
            event.reply("The 'All genres' option is only available for STATUS subscription type.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().hasPermission(MANAGE_SERVER)) {
            event.reply("You must have the 'Manage Server' permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        if (subscribe) {
            String channelId = event.getChannel().getId();
            subscriptionService.subscribe(guildId, channelId, subscriptionType, gameGenre);
            if (subscriptionType == SubscriptionType.STATUS) {
                statusMessageService.upsertStatusMessage(guildId, channelId, gameGenre);
            }
            event.reply("Subscribed this channel for " + subscriptionType.getValue() + " updates in " + gameGenre.getValue() + " genre.").setEphemeral(true).queue();
        } else {
            subscriptionService.unsubscribe(guildId, subscriptionType, gameGenre);
            if (subscriptionType == SubscriptionType.STATUS) {
                statusMessageService.deleteStatusMessage(guildId, gameGenre);
            }
            event.reply("Unsubscribed this server from " + subscriptionType.getValue() + " updates in " + gameGenre.getValue() + " genre.").setEphemeral(true).queue();
        }
    }
}
