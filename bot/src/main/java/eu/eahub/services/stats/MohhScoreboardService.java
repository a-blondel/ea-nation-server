package eu.eahub.services.stats;

import eu.eahub.entities.ChannelSubscriptionEntity;
import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.GameEntity;
import eu.eahub.entities.stats.MohhGameReportEntity;
import eu.eahub.enums.GameGenre;
import eu.eahub.enums.MohhMap;
import eu.eahub.enums.SubscriptionType;
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
import org.springframework.core.io.ClassPathResource;
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
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MohhScoreboardService {

    private final TemplateEngine templateEngine;
    private final DiscordBotService discordBotService;
    private final ChannelSubscriptionService channelSubscriptionService;
    @Value("${reports.path}")
    private String reportsPath;

    public void generateScoreboard(GameEntity game) {
        log.info("Generating scoreboard for game #{}", game.getId());
        try {
            Context baseContext = new Context();
            GameInfoResult gameInfo = setGameInfoIntoContextChunkable(baseContext, game);
            if (!gameInfo.proceed) {
                log.info("Skipping game #{}", game.getId());
                return;
            }

            try (InputStream cssStream = getClass().getResourceAsStream("/static/mohh/styles.css")) {
                if (cssStream == null) {
                    throw new IOException("Could not find resource styles.css");
                }
                String cssContent = new String(cssStream.readAllBytes());
                baseContext.setVariable("styles", cssContent);
            }

            setImagesIntoContext(baseContext);

            List<File> imageFiles = new ArrayList<>();
            String gameModeId = baseContext.getVariable("gameModeId").toString();
            if (gameModeId.equals("8")) {
                // Deathmatch: chunk all reports into groups of 16
                List<List<MohhGameReportEntity>> chunks = chunkList(gameInfo.dmReports, 16);
                for (int i = 0; i < chunks.size(); i++) {
                    Context context = cloneContext(baseContext);
                    context.setVariable("reports", chunks.get(i));
                    String winner = gameInfo.dmWinner;
                    context.setVariable("winner", winner == null ? "Draw Battle" : winner + " Wins the Battle");
                    String gameName = normalizeString(game.getName().replaceAll("\"", ""));
                    if (chunks.size() > 1) {
                        context.setVariable("gameName", gameName + " (" + (i + 1) + "/" + chunks.size() + ")");
                    } else {
                        context.setVariable("gameName", gameName);
                    }
                    String htmlContent = templateEngine.process("mohh/scoreboard-dm", context);
                    File imageFile = renderHtmlToImage(htmlContent, game.getId(), i + 1, chunks.size());
                    imageFiles.add(imageFile);
                }
            } else {
                // Team: chunk axis and allies separately, then pair up
                List<List<MohhGameReportEntity>> axisChunks = chunkList(gameInfo.axisReports, 16);
                List<List<MohhGameReportEntity>> alliesChunks = chunkList(gameInfo.alliesReports, 16);
                int maxChunks = Math.max(axisChunks.size(), alliesChunks.size());
                for (int i = 0; i < maxChunks; i++) {
                    Context context = cloneContext(baseContext);
                    context.setVariable("axisReports", i < axisChunks.size() ? axisChunks.get(i) : new ArrayList<>());
                    context.setVariable("alliesReports", i < alliesChunks.size() ? alliesChunks.get(i) : new ArrayList<>());
                    context.setVariable("axisTotalKills", gameInfo.axisTotalKills);
                    context.setVariable("axisTotalDeaths", gameInfo.axisTotalDeaths);
                    context.setVariable("alliesTotalKills", gameInfo.alliesTotalKills);
                    context.setVariable("alliesTotalDeaths", gameInfo.alliesTotalDeaths);
                    context.setVariable("winner", gameInfo.teamWinner);
                    String gameName = normalizeString(game.getName().replaceAll("\"", ""));
                    if (maxChunks > 1) {
                        context.setVariable("gameName", gameName + " (" + (i + 1) + "/" + maxChunks + ")");
                    } else {
                        context.setVariable("gameName", gameName);
                    }
                    String htmlContent = templateEngine.process("mohh/scoreboard-team", context);
                    File imageFile = renderHtmlToImage(htmlContent, game.getId(), i + 1, maxChunks);
                    imageFiles.add(imageFile);
                }
            }

            // Get the game genre based on the game's VERS
            GameGenre gameGenre = GameVersUtils.getGenreForVers(game.getVers());
            if (gameGenre == null) {
                log.warn("Unknown game genre for VERS: {}, defaulting to FPS", game.getVers());
                gameGenre = GameGenre.FPS;
            }

            // Get subscribers for the specific game genre
            List<ChannelSubscriptionEntity> scoreboardSubs = channelSubscriptionService.getAllByTypeAndGenre(SubscriptionType.SCOREBOARD, gameGenre);
            List<String> channelIds = scoreboardSubs.stream().map(ChannelSubscriptionEntity::getChannelId).toList();

            if (!channelIds.isEmpty()) {
                discordBotService.sendImages(channelIds, imageFiles, null);
            }

        } catch (Exception e) {
            log.error("Error generating scoreboard for game #{}", game.getId(), e);
        }
    }

    // Normalize string for HTML display (NFKC normalization, remove problematic chars)
    private String normalizeString(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
        // Remove any non-printable/control characters
        normalized = normalized.replaceAll("[\\p{C}]", "");
        return normalized.trim();
    }

    // Helper to render HTML to image file
    private File renderHtmlToImage(String htmlContent, long gameId, int page, int totalPages) throws IOException {
        File htmlFile = File.createTempFile("scoreboard", ".html");
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
        String suffix = totalPages > 1 ? ("_" + page) : "";
        File imageFile = new File(imageDir, "scoreboard_#" + gameId + suffix + ".png");
        Files.copy(screenshot.toPath(), imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        driver.quit();
        htmlFile.delete();
        return imageFile;
    }

    // Helper to chunk a list
    private <T> List<List<T>> chunkList(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
    }

    // Helper to clone a Thymeleaf context
    private Context cloneContext(Context original) {
        Context clone = new Context();
        for (String key : original.getVariableNames()) {
            clone.setVariable(key, original.getVariable(key));
        }
        return clone;
    }

    // Get all reports for chunking
    private GameInfoResult setGameInfoIntoContextChunkable(Context context, GameEntity game) {
        context.setVariable("gameName", game.getName().replaceAll("\"", ""));
        String[] params = game.getParams().split(",");
        String gameModeId = params[0];
        context.setVariable("gameModeId", gameModeId);
        context.setVariable("mapHexId", params[1]);
        context.setVariable("mapName", MohhMap.getMapNameByHexId(params[1]));
        context.setVariable("friendlyFireMode", params[2]);
        context.setVariable("aimAssist", params[3]);
        context.setVariable("ranked", params[8]);
        context.setVariable("hasPassword", game.getPass() != null);
        context.setVariable("gameStartTime", game.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        context.setVariable("gameDuration", Duration.between(game.getStartTime(), game.getEndTime()).toMinutes());

        Set<MohhGameReportEntity> reports = game.getGameConnections().stream()
                .map(GameConnectionEntity::getMohhGameReport)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, MohhGameReportEntity> aggregatedReports = new HashMap<>();
        for (MohhGameReportEntity report : reports) {
            if (report.getGameConnection().getPersonaConnection().isHost()) {
                continue;
            }
            String personaConnectionId = report.getGameConnection().getPersonaConnection().getPersona().getPers();
            if (aggregatedReports.containsKey(personaConnectionId)) {
                MohhGameReportEntity existingReport = aggregatedReports.get(personaConnectionId);
                MohhGameReportEntity aggregatedReport = getAggregatedReport(report, existingReport);
                aggregatedReports.put(personaConnectionId, aggregatedReport);
            } else {
                aggregatedReports.put(personaConnectionId, report);
            }
        }

        GameInfoResult result = new GameInfoResult();
        if (aggregatedReports.size() < 2 || aggregatedReports.values().stream().noneMatch(report -> report.getKill() > 0)) {
            result.proceed = false;
            return result;
        }
        result.proceed = true;

        if (gameModeId.equals("8")) {
            // Deathmatch
            List<MohhGameReportEntity> sortedReports = aggregatedReports.values().stream()
                    .filter(report -> report.getKill() > 0 || report.getDeath() > 0)
                    .sorted((report1, report2) -> {
                        int score1 = report1.getKill() - report1.getDeath();
                        int score2 = report2.getKill() - report2.getDeath();
                        return Integer.compare(score2, score1);
                    })
                    .peek(report -> {
                        String persona = report.getGameConnection().getPersonaConnection().getPersona().getPers().replaceAll("\"", "");
                        report.getGameConnection().getPersonaConnection().getPersona().setPers(persona);
                    })
                    .toList();
            result.dmReports = sortedReports;
            result.dmWinner = sortedReports.stream()
                    .filter(report -> report.getWin() > 0)
                    .findFirst()
                    .map(report -> report.getGameConnection().getPersonaConnection().getPersona().getPers())
                    .orElse(null);
        } else {
            // Team
            List<MohhGameReportEntity> sortedAxisReports = aggregatedReports.values().stream()
                    .filter(report -> report.getKill() > 0 || report.getDeath() > 0)
                    .filter(report -> report.getAxis() > 0 || (report.getAllies() == 0 && isMostlyAxis(report)))
                    .sorted((report1, report2) -> {
                        int score1 = report1.getKill() - report1.getDeath();
                        int score2 = report2.getKill() - report2.getDeath();
                        return Integer.compare(score2, score1);
                    })
                    .peek(report -> {
                        String persona = report.getGameConnection().getPersonaConnection().getPersona().getPers().replaceAll("\"", "");
                        report.getGameConnection().getPersonaConnection().getPersona().setPers(persona);
                    })
                    .toList();

            List<MohhGameReportEntity> sortedAlliesReports = aggregatedReports.values().stream()
                    .filter(report -> report.getKill() > 0 || report.getDeath() > 0)
                    .filter(report -> report.getAllies() > 0 || (report.getAxis() == 0 && !isMostlyAxis(report)))
                    .sorted((report1, report2) -> {
                        int score1 = report1.getKill() - report1.getDeath();
                        int score2 = report2.getKill() - report2.getDeath();
                        return Integer.compare(score2, score1);
                    })
                    .peek(report -> {
                        String persona = report.getGameConnection().getPersonaConnection().getPersona().getPers().replaceAll("\"", "");
                        report.getGameConnection().getPersonaConnection().getPersona().setPers(persona);
                    })
                    .toList();

            result.axisReports = sortedAxisReports;
            result.alliesReports = sortedAlliesReports;
            result.axisTotalKills = sortedAxisReports.stream().mapToInt(MohhGameReportEntity::getKill).sum();
            result.axisTotalDeaths = sortedAxisReports.stream().mapToInt(MohhGameReportEntity::getDeath).sum();
            result.alliesTotalKills = sortedAlliesReports.stream().mapToInt(MohhGameReportEntity::getKill).sum();
            result.alliesTotalDeaths = sortedAlliesReports.stream().mapToInt(MohhGameReportEntity::getDeath).sum();
            result.teamWinner = sortedAxisReports.stream()
                    .filter(report -> report.getWin() > 0)
                    .findFirst()
                    .map(report -> "Axis")
                    .orElseGet(() -> sortedAlliesReports.stream()
                            .filter(report -> report.getWin() > 0)
                            .findFirst()
                            .map(report -> "Allies")
                            .orElse("Draw"));
        }
        return result;
    }

    private boolean isMostlyAxis(MohhGameReportEntity report) {
        int axisShots = report.getLugerShot() + report.getMp40Shot() + report.getMp44Shot() +
                report.getKarShot() + report.getGewrShot() + report.getPanzShot();
        int alliesShots = report.getColtShot() + report.getTomShot() + report.getBarShot() +
                report.getGarShot() + report.getEnfieldShot() + report.getBazShot();
        return axisShots > alliesShots;
    }

    private MohhGameReportEntity getAggregatedReport(MohhGameReportEntity report, MohhGameReportEntity existingReport) {
        MohhGameReportEntity aggregatedReport = new MohhGameReportEntity();
        aggregatedReport.setKill(existingReport.getKill() + report.getKill());
        aggregatedReport.setDeath(existingReport.getDeath() + report.getDeath());
        aggregatedReport.setHead(existingReport.getHead() + report.getHead());
        aggregatedReport.setHit(existingReport.getHit() + report.getHit());
        aggregatedReport.setShot(existingReport.getShot() + report.getShot());
        aggregatedReport.setWin(existingReport.getWin() + report.getWin());
        aggregatedReport.setLoss(existingReport.getLoss() + report.getLoss());
        aggregatedReport.setDmRnd(existingReport.getDmRnd() + report.getDmRnd());
        aggregatedReport.setAllies(existingReport.getAllies() + report.getAllies());
        aggregatedReport.setAxis(existingReport.getAxis() + report.getAxis());
        aggregatedReport.setPlayTime(existingReport.getPlayTime() + report.getPlayTime());
        aggregatedReport.setGameConnectionId(existingReport.getGameConnectionId());
        aggregatedReport.setGameConnection(existingReport.getGameConnection());
        return aggregatedReport;
    }

    private void setImagesIntoContext(Context context) throws IOException {
        String mapHexId = context.getVariable("mapHexId").toString();
        Resource mapImageResource = findImageByMapHexId(mapHexId);
        setImageIntoContext(context, mapImageResource, "backgroundImg");

        setImageIntoContext(context, new ClassPathResource("/static/mohh/images/logout.png"), "logoutImg");

        if (context.getVariable("hasPassword").toString().equals("true")) {
            setImageIntoContext(context, new ClassPathResource("/static/mohh/images/password.png"), "passwordImg");
        }

        if (context.getVariable("ranked").toString().equals("1")) {
            setImageIntoContext(context, new ClassPathResource("/static/mohh/images/ranked.png"), "rankedImg");
        }

        String friendlyFireMode = context.getVariable("friendlyFireMode").toString();
        if (friendlyFireMode.equals("1") || friendlyFireMode.equals("2")) {
            Resource friendlyFireResource = friendlyFireMode.equals("1")
                    ? new ClassPathResource("/static/mohh/images/friendly-fire.png")
                    : new ClassPathResource("/static/mohh/images/reverse-friendly-fire.png");
            setImageIntoContext(context, friendlyFireResource, "friendlyFireImg");
        }

        if (context.getVariable("aimAssist").toString().equals("1")) {
            setImageIntoContext(context, new ClassPathResource("/static/mohh/images/aim-assist.png"), "aimAssistImg");
        }
    }

    private Resource findImageByMapHexId(String mapHexId) throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/static/mohh/images/maps/" + mapHexId + "*.jpg");
        if (resources.length > 0) {
            return resources[0];
        } else {
            throw new IOException("No image found for mapHexId: " + mapHexId);
        }
    }

    private void setImageIntoContext(Context context, Resource resource, String variableName) throws IOException {
        try (InputStream imageStream = resource.getInputStream()) {
            byte[] imageBytes = imageStream.readAllBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String Img = "data:image/png;base64," + base64Image;
            context.setVariable(variableName, Img);
        }
    }

    // Helper class to return all info needed for chunking
    private static class GameInfoResult {
        boolean proceed;
        List<MohhGameReportEntity> dmReports;
        String dmWinner;
        List<MohhGameReportEntity> axisReports;
        List<MohhGameReportEntity> alliesReports;
        int axisTotalKills;
        int axisTotalDeaths;
        int alliesTotalKills;
        int alliesTotalDeaths;
        String teamWinner;
    }
}

