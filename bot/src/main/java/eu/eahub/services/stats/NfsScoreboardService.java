package eu.eahub.services.stats;

import eu.eahub.entities.ChannelSubscriptionEntity;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.stats.NfsGameReportEntity;
import eu.eahub.enums.GameGenre;
import eu.eahub.enums.SubscriptionType;
import eu.eahub.enums.nfs.*;
import eu.eahub.repositories.stats.NfsGameReportRepository;
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
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class NfsScoreboardService {

    private static final String PSP_NFS_06 = "PSP_NFS06"; // Most Wanted
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final TemplateEngine templateEngine;
    private final DiscordBotService discordBotService;
    private final ChannelSubscriptionService channelSubscriptionService;
    private final NfsGameReportRepository nfsGameReportRepository;
    @Value("${reports.path}")
    private String reportsPath;

    public void generateScoreboard(GameEntity game) {
        log.info("Generating NFS scoreboard for game #{}", game.getId());
        try {
            // Extract all reports from game connections
            List<NfsGameReportEntity> reports = game.getGameConnections().stream()
                    .map(GameConnectionEntity::getNfsGameReport)
                    .filter(Objects::nonNull)
                    .toList();

            if (reports.isEmpty()) {
                log.info("No NFS game reports found for game #{}", game.getId());
                return;
            }

            // Check if at least one player completed the race (racetime > 0 or pos > 0)
            boolean hasValidRace = reports.stream()
                    .anyMatch(r -> (r.getRacetime() != null && r.getRacetime() > 0)
                            || (r.getPos() != null && r.getPos() > 0));

            if (!hasValidRace) {
                log.info("Skipping game #{} - no valid race results", game.getId());
                return;
            }

            // Sort reports by position
            // Special case for Most Wanted: pos=0 means loser
            List<NfsGameReportEntity> sortedReports = reports.stream()
                    .sorted((r1, r2) -> {
                        int pos1 = r1.getPos();
                        int pos2 = r2.getPos();

                        if (PSP_NFS_06.equals(game.getVers())) {
                            if (pos1 == 0) {
                                pos1 = 2;
                            }
                            if (pos2 == 0) {
                                pos2 = 2;
                            }
                        }

                        return Integer.compare(pos1, pos2);
                    })
                    .toList();

            // Build Thymeleaf context
            Context context = new Context();

            // Load CSS
            try (InputStream cssStream = getClass().getResourceAsStream("/static/nfs/styles.css")) {
                if (cssStream == null) {
                    throw new IOException("Could not find resource /static/nfs/styles.css");
                }
                String cssContent = new String(cssStream.readAllBytes());
                context.setVariable("styles", cssContent);
            }

            // Load background images
            setImagesIntoContext(context);

            // Get first report for race metadata (all reports have same venue, dir, rnk)
            NfsGameReportEntity firstReport = reports.getFirst();

            // Determine background image key based on game version
            String backgroundKey = getBackgroundImageKey(game.getVers());
            context.setVariable("backgroundKey", backgroundKey);

            // Track information
            String trackName = getTrackName(firstReport.getVenue(), game.getVers());
            context.setVariable("trackName", trackName);

            // Direction - always display for all games
            String direction = "";
            if (firstReport.getDir() != null) {
                direction = NfsDirection.fromValue(firstReport.getDir()).getLabel();
            }
            context.setVariable("direction", direction);

            // Ranked status
            boolean isRanked = firstReport.getRnk() != null && firstReport.getRnk() == 1;
            context.setVariable("ranked", isRanked ? "ON" : "OFF");

            // Game date/time
            String gameDateTime = game.getEndTime() != null
                    ? game.getEndTime().format(DATE_FORMATTER)
                    : game.getStartTime().format(DATE_FORMATTER);
            context.setVariable("gameDateTime", gameDateTime);

            // Check if Most Wanted (different handling for time/lap)
            boolean isMostWanted = PSP_NFS_06.equals(game.getVers());
            context.setVariable("isMostWanted", isMostWanted);

            // Get world record for this track/direction
            // MW uses racetime, others use lap
            Integer worldRecord;
            if (isMostWanted) {
                worldRecord = nfsGameReportRepository.findBestRacetimeForTrack(
                        game.getVers(), firstReport.getVenue(), firstReport.getDir());
            } else {
                worldRecord = nfsGameReportRepository.findBestLapForTrack(
                        game.getVers(), firstReport.getVenue(), firstReport.getDir());
            }

            // Prepare report data for template
            List<RaceResult> raceResults = new ArrayList<>();
            for (NfsGameReportEntity report : sortedReports) {
                RaceResult result = new RaceResult();

                // Rank - use actual pos value, with MW special handling
                Integer pos = report.getPos();
                if (pos != null && pos > 0) {
                    result.rank = pos;
                } else {
                    result.rank = 2;
                }

                // Player name
                String playerName = report.getGameConnection().getPersonaConnection()
                        .getPersona().getPers();
                result.playerName = normalizeString(playerName.replaceAll("\"", ""));

                // Car name
                result.carName = getCarName(report.getCar(), game.getVers());

                // Time - hide for Most Wanted (game doesn't send race time)
                if (isMostWanted) {
                    result.time = null; // Will be hidden in template
                } else {
                    // Check if DNF
                    boolean isDNF = !Objects.equals(report.getLapscomp(), report.getNumlaps()) && report.getGtyp() != 15; // Gtyp 15 = Gateway mode from Undercover
                    if (isDNF) {
                        result.time = "DNF";
                    } else {
                        result.time = formatTime(report.getRacetime());
                    }
                }

                // Best lap with world record indicator
                Integer currentBestTime;
                if (isMostWanted) {
                    // MW uses racetime for best lap display
                    currentBestTime = report.getRacetime();
                } else {
                    // Others use lap field
                    currentBestTime = report.getLap();
                }

                if (currentBestTime != null && currentBestTime > 0) {
                    // Check if this time equals the world record
                    if (isRanked && (worldRecord == 0 || currentBestTime.equals(worldRecord))) {
                        result.bestLap = "🏆 ";
                    } else {
                        result.bestLap = "";
                    }
                    result.bestLap += formatTime(currentBestTime);
                } else {
                    result.bestLap = "-";
                }

                raceResults.add(result);
            }

            context.setVariable("results", raceResults);

            // Determine winner
            String winner = sortedReports.stream()
                    .filter(r -> r.getPos() != null && r.getPos() == 1)
                    .findFirst()
                    .map(r -> normalizeString(r.getGameConnection().getPersonaConnection()
                            .getPersona().getPers().replaceAll("\"", "")))
                    .orElse(null);
            context.setVariable("winner", winner);

            // Render template
            String htmlContent = templateEngine.process("nfs/scoreboard", context);
            File imageFile = renderHtmlToImage(htmlContent, game.getId());

            // Post to Discord
            GameGenre gameGenre = GameVersUtils.getGenreForVers(game.getVers());
            if (gameGenre == null) {
                log.warn("Unknown game genre for VERS: {}, defaulting to RACING", game.getVers());
                gameGenre = GameGenre.RACING;
            }

            List<ChannelSubscriptionEntity> scoreboardSubs = channelSubscriptionService
                    .getAllByTypeAndGenre(SubscriptionType.SCOREBOARD, gameGenre);
            List<String> channelIds = scoreboardSubs.stream()
                    .map(ChannelSubscriptionEntity::getChannelId)
                    .toList();

            if (!channelIds.isEmpty()) {
                discordBotService.sendImages(channelIds, List.of(imageFile), null);
            }

        } catch (Exception e) {
            log.error("Error generating NFS scoreboard for game #{}", game.getId(), e);
        }
    }

    /**
     * Get the car name based on the car ID and game version.
     */
    private String getCarName(Integer carId, String vers) {
        if (carId == null) return "UNKNOWN";

        return switch (vers) {
            case "PSP_NFS06" -> MostWantedCar.getModelName(carId);
            case "PSP_NFS07" -> CarbonCar.getModelName(carId);
            case "PSP_NFS08" -> ProStreetCar.getModelName(carId);
            case "PSP_NFS09" -> UndercoverCar.getModelName(carId);
            default -> "UNKNOWN";
        };
    }

    /**
     * Get the track name based on the venue ID and game version.
     */
    private String getTrackName(Integer venueId, String vers) {
        if (venueId == null) return "UNKNOWN";

        return switch (vers) {
            case "PSP_NFS06" -> MostWantedTrack.getTrackName(venueId);
            case "PSP_NFS07" -> CarbonTrack.getTrackName(venueId);
            case "PSP_NFS08" -> ProStreetTrack.getTrackName(venueId);
            case "PSP_NFS09" -> UndercoverTrack.getTrackName(venueId);
            default -> "UNKNOWN";
        };
    }

    /**
     * Get the background image key for the template based on game version.
     */
    private String getBackgroundImageKey(String vers) {
        return switch (vers) {
            case "PSP_NFS07" -> "carbonImage";
            case "PSP_NFS08" -> "prostreetImage";
            case "PSP_NFS09" -> "undercoverImage";
            default -> "mostwantedImage";
        };
    }

    /**
     * Format time in milliseconds to MM:SS.mmm format.
     */
    private String formatTime(Integer timeMs) {
        if (timeMs == null || timeMs == 0) return "DNF";

        int totalSeconds = timeMs / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        int milliseconds = timeMs % 1000;

        return String.format("%d:%02d.%03d", minutes, seconds, milliseconds);
    }

    /**
     * Load background images into context as base64 data URIs.
     */
    private void setImagesIntoContext(Context context) throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/static/nfs/*.jpg");

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename != null) {
                String varName = filename.replace(".jpg", "Image");
                try (InputStream is = resource.getInputStream()) {
                    byte[] imageBytes = is.readAllBytes();
                    String base64 = Base64.getEncoder().encodeToString(imageBytes);
                    context.setVariable(varName, "data:image/jpeg;base64," + base64);
                }
            }
        }
    }

    /**
     * Render HTML to PNG image using headless Chrome.
     */
    private File renderHtmlToImage(String htmlContent, long gameId) throws IOException {
        File htmlFile = File.createTempFile("nfs_scoreboard", ".html");
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

        File imageFile = new File(imageDir, "nfs_scoreboard_#" + gameId + ".png");
        Files.copy(screenshot.toPath(), imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        driver.quit();
        htmlFile.delete();

        return imageFile;
    }

    /**
     * Normalize string for HTML display.
     */
    private String normalizeString(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
        normalized = normalized.replaceAll("[\\p{C}]", "");
        return normalized.trim();
    }

    /**
     * Inner class to hold race result data for template.
     */
    public static class RaceResult {
        public int rank;
        public String playerName;
        public String carName;
        public String time;
        public String bestLap;
    }
}
