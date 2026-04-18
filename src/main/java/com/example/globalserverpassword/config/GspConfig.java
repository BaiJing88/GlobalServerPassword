package com.example.globalserverpassword.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * 全服密码配置管理
 * 配置文件存储在 config/globalserverpassword.properties
 */
public class GspConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(GspConfig.class);
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "globalserverpassword.properties";
    private static GspConfig INSTANCE;

    private final Properties props = new Properties();
    private final Path configPath;

    // ====== 密码 ======
    /** 存储的密码 SHA-256 哈希（Hex 字符串） */
    private String passwordHash = "";

    // ====== 踢出控制 ======
    /** 是否启用连续错误次数限制 */
    private boolean wrongAttemptsEnabled = true;
    /** 允许的最大错误次数 */
    private int maxWrongAttempts = 3;
    /** 是否启用超时踢出 */
    private boolean timeoutEnabled = true;
    /** 超时秒数（未认证时） */
    private int timeoutSeconds = 120;

    // ====== 定时换密码 ======
    /** 是否启用定时换密码 */
    private boolean autoRotateEnabled = false;
    /**
     * 定时换密码模式: "interval"（计时模式，每N秒） / "scheduled"（定时模式，每天HH:mm:ss）
     */
    private String autoRotateMode = "interval";
    /** 计时模式：每隔多少秒换一次密码 */
    private long autoRotateIntervalSeconds = 3600;
    /** 定时模式：每天换密码的时刻，格式 HH:mm:ss，例如 "06:00:00" */
    private String autoRotateScheduledTime = "06:00:00";

    private GspConfig() {
        configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
    }

    public static GspConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GspConfig();
        }
        return INSTANCE;
    }

    /** 从文件加载配置，如文件不存在则使用默认值并保存 */
    public void load() {
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.exists(configPath)) {
                try (InputStream in = Files.newInputStream(configPath)) {
                    props.load(in);
                }
                readFromProps();
                LOGGER.info("[GlobalServerPassword] 配置文件已加载: {}", configPath.toAbsolutePath());
            } else {
                LOGGER.info("[GlobalServerPassword] 未找到配置文件，将使用默认配置并保存");
                save();
            }
        } catch (IOException e) {
            LOGGER.error("[GlobalServerPassword] 配置文件加载失败", e);
        }
    }

    private void readFromProps() {
        passwordHash = props.getProperty("password_hash", "");
        wrongAttemptsEnabled = Boolean.parseBoolean(props.getProperty("wrong_attempts_enabled", "true"));
        maxWrongAttempts = intProp("max_wrong_attempts", 3);
        timeoutEnabled = Boolean.parseBoolean(props.getProperty("timeout_enabled", "true"));
        timeoutSeconds = intProp("timeout_seconds", 120);
        autoRotateEnabled = Boolean.parseBoolean(props.getProperty("auto_rotate_enabled", "false"));
        autoRotateMode = props.getProperty("auto_rotate_mode", "interval");
        autoRotateIntervalSeconds = longProp("auto_rotate_interval_seconds", 3600);
        autoRotateScheduledTime = props.getProperty("auto_rotate_scheduled_time", "06:00:00");
    }

    /** 将当前配置保存到文件 */
    public void save() {
        props.setProperty("password_hash", passwordHash);
        props.setProperty("wrong_attempts_enabled", String.valueOf(wrongAttemptsEnabled));
        props.setProperty("max_wrong_attempts", String.valueOf(maxWrongAttempts));
        props.setProperty("timeout_enabled", String.valueOf(timeoutEnabled));
        props.setProperty("timeout_seconds", String.valueOf(timeoutSeconds));
        props.setProperty("auto_rotate_enabled", String.valueOf(autoRotateEnabled));
        props.setProperty("auto_rotate_mode", autoRotateMode);
        props.setProperty("auto_rotate_interval_seconds", String.valueOf(autoRotateIntervalSeconds));
        props.setProperty("auto_rotate_scheduled_time", autoRotateScheduledTime);

        try (OutputStream out = Files.newOutputStream(configPath)) {
            props.store(out,
                "GlobalServerPassword Configuration\n" +
                "# password_hash          - SHA-256 hash of the server password\n" +
                "# wrong_attempts_enabled - enable kick on too many wrong attempts (true/false)\n" +
                "# max_wrong_attempts     - how many wrong attempts before kick\n" +
                "# timeout_enabled        - enable kick on auth timeout (true/false)\n" +
                "# timeout_seconds        - seconds before unauthenticated player is kicked\n" +
                "# auto_rotate_enabled    - enable automatic password rotation (true/false)\n" +
                "# auto_rotate_mode       - 'interval' or 'scheduled'\n" +
                "# auto_rotate_interval_seconds - (interval mode) seconds between rotations\n" +
                "# auto_rotate_scheduled_time   - (scheduled mode) daily time HH:mm:ss"
            );
        } catch (IOException e) {
            LOGGER.error("[GlobalServerPassword] 配置文件保存失败", e);
        }
    }

    private int intProp(String key, int def) {
        try { return Integer.parseInt(props.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private long longProp(String key, long def) {
        try { return Long.parseLong(props.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    // ====== Getters / Setters ======

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String hash) {
        this.passwordHash = hash;
        props.setProperty("password_hash", hash);
        save();
    }

    /** 获取当前密码版本（用于IP白名单失效） */
    public int getPasswordVersion() {
        String val = props.getProperty("password_version", "0");
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return 0; }
    }

    /** 增加密码版本（密码更改时调用，使所有IP白名单失效） */
    public void incrementPasswordVersion() {
        int newVersion = getPasswordVersion() + 1;
        props.setProperty("password_version", String.valueOf(newVersion));
        save();
    }

    public boolean isWrongAttemptsEnabled() { return wrongAttemptsEnabled; }
    public int getMaxWrongAttempts() { return maxWrongAttempts; }
    public boolean isTimeoutEnabled() { return timeoutEnabled; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public boolean isAutoRotateEnabled() { return autoRotateEnabled; }
    public String getAutoRotateMode() { return autoRotateMode; }
    public long getAutoRotateIntervalSeconds() { return autoRotateIntervalSeconds; }
    public String getAutoRotateScheduledTime() { return autoRotateScheduledTime; }
}
