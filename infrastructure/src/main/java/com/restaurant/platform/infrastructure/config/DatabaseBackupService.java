package com.restaurant.platform.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@EnableScheduling
public class DatabaseBackupService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);
    private static final Pattern JDBC_PATTERN = Pattern.compile("jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?]+)");

    @Value("${app.backup.enabled:false}")
    private boolean backupEnabled;

    @Value("${app.backup.dir:./backups}")
    private String backupDir;

    @Value("${app.backup.retention-days:14}")
    private int retentionDays;

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    @Value("${spring.datasource.username:root}")
    private String dbUser;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Scheduled(cron = "${app.backup.cron:0 0 3 * * *}")
    public void runDailyBackup() {
        if (!backupEnabled) {
            return;
        }
        Matcher m = JDBC_PATTERN.matcher(jdbcUrl);
        if (!m.find()) {
            log.error("Database backup skipped: unsupported JDBC URL format");
            return;
        }
        String host = m.group(1);
        String port = m.group(2) != null ? m.group(2) : "3306";
        String dbName = m.group(3);

        try {
            Path dir = Paths.get(backupDir);
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path targetFile = dir.resolve(dbName + "_" + stamp + ".sql");

            ProcessBuilder pb = new ProcessBuilder(
                    "mysqldump",
                    "-h", host,
                    "-P", port,
                    "-u", dbUser,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    dbName
            );
            pb.environment().put("MYSQL_PWD", dbPassword);
            pb.redirectOutput(targetFile.toFile());
            pb.redirectError(ProcessBuilder.Redirect.PIPE);

            Process process = pb.start();
            int exit = process.waitFor();
            if (exit == 0) {
                log.info("Database backup created successfully: {}", targetFile);
                cleanupOldBackups(dir);
            } else {
                Files.deleteIfExists(targetFile);
                String err = new String(process.getErrorStream().readAllBytes());
                log.error("Database backup failed with exit code {}: {}", exit, err);
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Database backup error: {}", ex.getMessage(), ex);
        }
    }

    private void cleanupOldBackups(Path dir) throws IOException {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(Path::toString).reversed())
                    .forEach(p -> {
                        try {
                            File f = p.toFile();
                            var lastMod = java.time.Instant.ofEpochMilli(f.lastModified())
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                            if (lastMod.isBefore(cutoff)) {
                                Files.deleteIfExists(p);
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}