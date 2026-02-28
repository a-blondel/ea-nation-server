package eu.eahub.services.stats;

import eu.eahub.entities.ChannelSubscriptionEntity;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.core.PersonaEntity;
import eu.eahub.entities.stats.NhlGameReportEntity;
import eu.eahub.entities.stats.NhlPersonaStatsEntity;
import eu.eahub.enums.GameGenre;
import eu.eahub.enums.NhlTeam;
import eu.eahub.enums.SubscriptionType;
import eu.eahub.repositories.stats.NhlPersonaStatsRepository;
import eu.eahub.services.discord.ChannelSubscriptionService;
import eu.eahub.services.discord.DiscordBotService;
import eu.eahub.utils.GameVersUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NhlScoreboardService {

    private final TemplateEngine templateEngine;
    private final DiscordBotService discordBotService;
    private final ChannelSubscriptionService channelSubscriptionService;
    private final NhlPersonaStatsRepository nhlPersonaStatsRepository;
    @Value("${reports.path}")
    private String reportsPath;

    public void generateScoreboard(GameEntity game) {
        log.info("Generating scoreboard for game #{}", game.getId());

        try {
            Context context = new Context();

            // Get game connections (players)
            List<GameConnectionEntity> connections = game.getGameConnections().stream()
                    .filter(gc -> gc.getNhlGameReport() != null)
                    .toList();

            if (connections.size() != 2) {
                log.warn("NHL game #{} does not have exactly 2 game reports, skipping", game.getId());
                return;
            }

            // Check that at least one player has a score > 0
            boolean hasValidScore = connections.stream()
                    .anyMatch(gc -> gc.getNhlGameReport().getScore() > 0);

            if (!hasValidScore) {
                log.warn("NHL game #{} has no valid scores (all scores are 0), skipping", game.getId());
                return;
            }

            // Identify home/away players
            GameConnectionEntity homeConnection = connections.stream()
                    .filter(gc -> gc.getNhlGameReport().getHome() == 1)
                    .findFirst().orElse(connections.get(0));

            GameConnectionEntity awayConnection = connections.stream()
                    .filter(gc -> gc.getNhlGameReport().getHome() == 0)
                    .findFirst().orElse(connections.get(1));

            // Get persona entities and reports
            PersonaEntity homePlayer = homeConnection.getPersonaConnection().getPersona();
            PersonaEntity awayPlayer = awayConnection.getPersonaConnection().getPersona();
            NhlGameReportEntity homeGameReport = homeConnection.getNhlGameReport();
            NhlGameReportEntity awayGameReport = awayConnection.getNhlGameReport();

            // Get player stats
            NhlPersonaStatsEntity homePlayerStats = getPersonaStats(homePlayer, game.getVers());
            NhlPersonaStatsEntity awayPlayerStats = getPersonaStats(awayPlayer, game.getVers());

            // Get player ranks
            String homePlayerRank = getPlayerRank(homePlayer, game.getVers());
            String awayPlayerRank = getPlayerRank(awayPlayer, game.getVers());

            // Populate context with player data
            context.setVariable("homePlayer", homePlayer);
            context.setVariable("awayPlayer", awayPlayer);
            context.setVariable("homeGameReport", homeGameReport);
            context.setVariable("awayGameReport", awayGameReport);
            context.setVariable("homePlayerStats", homePlayerStats);
            context.setVariable("awayPlayerStats", awayPlayerStats);
            context.setVariable("homePlayerRank", homePlayerRank);
            context.setVariable("awayPlayerRank", awayPlayerRank);

            // Scores and teams
            context.setVariable("homeScore", homeGameReport.getScore());
            context.setVariable("awayScore", awayGameReport.getScore());

            // Load CSS and images
            loadCssAndImages(context);

            // Load team logos and stadium background
            loadTeamLogosAndBackground(context, homeGameReport, awayGameReport);

            // Add team names
            context.setVariable("homeTeamName", getTeamName(homeGameReport.getTeam()));
            context.setVariable("awayTeamName", getTeamName(awayGameReport.getTeam()));

            // Calculate special statistics
            context.setVariable("homeWinStreak", homePlayerStats != null ? homePlayerStats.getStreak() : 0);
            context.setVariable("awayWinStreak", awayPlayerStats != null ? awayPlayerStats.getStreak() : 0);

            // Calculate game statistics
            context.setVariable("homePowerPlay", formatPowerPlay(homeGameReport));
            context.setVariable("awayPowerPlay", formatPowerPlay(awayGameReport));

            // Check if game went to overtime
            context.setVariable("overtime", homeGameReport.getOt() == 1 || awayGameReport.getOt() == 1);

            // Game metadata - check bit 18 for ranked
            context.setVariable("ranked", isRankedGame(game.getSysflags()));
            context.setVariable("league", getLeagueFromParams(game.getParams()));

            // Generate HTML and image
            String htmlContent = templateEngine.process("nhl/template", context);
            File imageFile = renderHtmlToImage(htmlContent, game.getId());

            // Get the game genre based on the game's VERS
            GameGenre gameGenre = GameVersUtils.getGenreForVers(game.getVers());
            if (gameGenre == null) {
                log.warn("Unknown game genre for VERS: {}, defaulting to HOCKEY", game.getVers());
                gameGenre = GameGenre.HOCKEY;
            }

            // Get subscribers for the specific game genre
            List<ChannelSubscriptionEntity> scoreboardSubs = channelSubscriptionService.getAllByTypeAndGenre(SubscriptionType.SCOREBOARD, gameGenre);
            List<String> channelIds = scoreboardSubs.stream().map(ChannelSubscriptionEntity::getChannelId).toList();

            if (!channelIds.isEmpty()) {
                discordBotService.sendImages(channelIds, Collections.singletonList(imageFile), null);
            }

        } catch (Exception e) {
            log.error("Error generating NHL scoreboard for game #{}", game.getId(), e);
        }
    }

    private NhlPersonaStatsEntity getPersonaStats(PersonaEntity persona, String vers) {
        return nhlPersonaStatsRepository.findByPersonaIdAndVers(persona.getId(), vers);
    }

    private String getPlayerRank(PersonaEntity persona, String vers) {
        Long rank = nhlPersonaStatsRepository.getRankByPersonaIdAndVers(persona.getId(), vers);
        return rank != null ? "#" + rank : "N/A";
    }

    private String formatPowerPlay(NhlGameReportEntity report) {
        return report.getPpg() + "/" + report.getPpo();
    }

    private boolean isRankedGame(String sysflags) {
        if (sysflags == null || sysflags.isEmpty()) {
            return false;
        }

        try {
            int flags = Integer.parseInt(sysflags);
            // Check bit 18
            return (flags & (1 << 18)) != 0;
        } catch (NumberFormatException e) {
            log.warn("Could not parse sysflags: {}", sysflags);
            return false;
        }
    }

    private String getLeagueFromParams(String params) {
        if (params == null || params.isEmpty()) {
            return "NHL";
        }

        // Params is in fact base64 encoded, but we don't need to decode it here
        try {
            // Look for the 8th character (index 7) which should contain league information
            if (params.length() >= 8) {
                char leagueChar = params.charAt(7); // 8th character (0-based index)

                return switch (leagueChar) {
                    case 'A' -> "NHL";
                    case 'B' -> "NATIONAL";
                    case 'C' -> "ELITSERIEN";
                    case 'D' -> "SM-LIIGA";
                    case 'E' -> "DEL";
                    case 'F' -> "EXTRALIGA";
                    default -> "NHL"; // Default to NHL for unknown values
                };
            }
            return "NHL"; // Default fallback if string too short

        } catch (Exception e) {
            log.warn("Could not parse game params: {}, using default NHL", params, e);
            return "NHL";
        }
    }

    private void loadCssAndImages(Context context) throws IOException {
        try (InputStream cssStream = getClass().getResourceAsStream("/static/nhl/styles.css")) {
            if (cssStream == null) {
                throw new IOException("Could not find NHL styles.css");
            }
            String cssContent = new String(cssStream.readAllBytes());
            context.setVariable("styles", cssContent);
        }

        Map<String, String> images = new HashMap<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath:static/nhl/images/*.png");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    try (InputStream imageStream = resource.getInputStream()) {
                        byte[] imageBytes = imageStream.readAllBytes();
                        String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
                        String key = filename.substring(0, filename.lastIndexOf('.'));
                        images.put(key, base64Image);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Could not load NHL images", e);
        }
        context.setVariable("images", images);
    }

    private void loadTeamLogosAndBackground(Context context, NhlGameReportEntity homeGameReport, NhlGameReportEntity awayGameReport) {
        // Load team logos as base64
        loadTeamLogoIntoContext(context, homeGameReport.getTeam(), "homeTeamLogo");
        loadTeamLogoIntoContext(context, awayGameReport.getTeam(), "awayTeamLogo");

        // Load stadium background as base64
        loadStadiumBackgroundIntoContext(context, homeGameReport.getVenue());
    }

    private void loadTeamLogoIntoContext(Context context, int teamId, String variableName) {
        try {
            String logoPath = NhlTeam.getLogoPathById(teamId);
            Resource logoResource = new org.springframework.core.io.ClassPathResource("/static/nhl/images/" + logoPath);
            setImageIntoContext(context, logoResource, variableName);
        } catch (IOException e) {
            log.warn("Could not load team logo for teamId {}: {}", teamId, e.getMessage());
            // Fallback to default logo
            try {
                Resource defaultLogo = new org.springframework.core.io.ClassPathResource("/static/nhl/images/TeamLogos/Default.png");
                setImageIntoContext(context, defaultLogo, variableName);
            } catch (IOException ex) {
                log.error("Could not load default team logo", ex);
                context.setVariable(variableName, "");
            }
        }
    }

    private void loadStadiumBackgroundIntoContext(Context context, int venueId) {
        try {
            String backgroundPath = NhlTeam.getBackgroundPathByVenueId(venueId);
            Resource backgroundResource = new org.springframework.core.io.ClassPathResource("/static/nhl/images/" + backgroundPath);
            setImageIntoContext(context, backgroundResource, "stadiumBackground");
        } catch (IOException e) {
            log.warn("Could not load stadium background for venueId {}: {}", venueId, e.getMessage());
            // Fallback to default background
            try {
                Resource defaultBackground = new org.springframework.core.io.ClassPathResource("/static/nhl/images/Backgrounds/Default.jpg");
                setImageIntoContext(context, defaultBackground, "stadiumBackground");
            } catch (IOException ex) {
                log.error("Could not load default stadium background", ex);
                context.setVariable("stadiumBackground", "");
            }
        }
    }

    private void setImageIntoContext(Context context, Resource resource, String variableName) throws IOException {
        try (InputStream imageStream = resource.getInputStream()) {
            byte[] imageBytes = imageStream.readAllBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:image/" + getImageFormat(resource.getFilename()) + ";base64," + base64Image;
            context.setVariable(variableName, dataUrl);
        }
    }

    private String getImageFormat(String filename) {
        if (filename != null && filename.toLowerCase().endsWith(".jpg")) {
            return "jpeg";
        }
        return "png";
    }

    private File renderHtmlToImage(String htmlContent, long gameId) throws IOException {
        File htmlFile = File.createTempFile("nhl_scoreboard", ".html");
        Files.writeString(htmlFile.toPath(), htmlContent);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new",
                "--disable-extensions",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080",
                "--hide-scrollbars",
                "--allow-file-access-from-files");

        WebDriver driver = new ChromeDriver(options);
        driver.get(htmlFile.toURI().toString());
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

        File imageDir = new File(reportsPath);
        if (!imageDir.exists()) {
            imageDir.mkdirs();
        }

        File imageFile = new File(imageDir, "scoreboard_#" + gameId + ".png");
        Files.copy(screenshot.toPath(), imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        driver.quit();
        htmlFile.delete();

        return imageFile;
    }

    private String getTeamName(int teamId) {
        return NhlTeam.getTeamNameById(teamId);
    }
}
