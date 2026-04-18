package com.example.globalserverpassword.manager;

import com.example.globalserverpassword.GlobalServerPassword;
import com.example.globalserverpassword.config.GspConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 密码管理器：负责密码初始化、哈希、验证、定时轮换
 */
public class PasswordManager {
    private static PasswordManager INSTANCE;
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 8;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GSP-AutoRotate");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> rotateTask;

    private PasswordManager() {}

    public static PasswordManager getInstance() {
        if (INSTANCE == null) INSTANCE = new PasswordManager();
        return INSTANCE;
    }

    /**
     * 初始化：首次使用时生成随机密码，之后按配置启动定时轮换
     */
    public void init() {
        GspConfig cfg = GspConfig.getInstance();
        if (cfg.getPasswordHash() == null || cfg.getPasswordHash().isEmpty()) {
            // 首次使用，生成随机密码
            String newPass = generateRandomPassword();
            cfg.setPasswordHash(hash(newPass));
            GlobalServerPassword.LOGGER.warn("╔══════════════════════════════════════════════════════╗");
            GlobalServerPassword.LOGGER.warn("║        [全服密码] 初始密码已生成，请务必记住！           ║");
            GlobalServerPassword.LOGGER.warn("║  密码: {}                                           ║", newPass);
            GlobalServerPassword.LOGGER.warn("║  该密码只显示一次，无法再次获取，务必立即记录！           ║");
            GlobalServerPassword.LOGGER.warn("╚══════════════════════════════════════════════════════╝");
        }
        startAutoRotateIfEnabled();
    }

    /**
     * 验证输入的密码是否正确
     */
    public boolean verify(String input) {
        String cfg = GspConfig.getInstance().getPasswordHash();
        if (cfg == null || cfg.isEmpty()) return false;
        return cfg.equals(hash(input));
    }

    /**
     * 强制设置新密码（OP命令用）
     */
    public void setPassword(String plainPassword) {
        GspConfig.getInstance().setPasswordHash(hash(plainPassword));
        GlobalServerPassword.LOGGER.info("[GlobalServerPassword] 密码已由OP更新");
    }

    /**
     * 生成新的随机密码并存储哈希，然后打印到控制台
     */
    public String rotatePassword() {
        String newPass = generateRandomPassword();
        GspConfig.getInstance().setPasswordHash(hash(newPass));
        GlobalServerPassword.LOGGER.warn("╔══════════════════════════════════════════════════════╗");
        GlobalServerPassword.LOGGER.warn("║   [全服密码] 密码已自动更新！新密码如下：                 ║");
        GlobalServerPassword.LOGGER.warn("║   新密码: {}                                         ║", newPass);
        GlobalServerPassword.LOGGER.warn("║   该密码只显示一次，务必立即记录！                        ║");
        GlobalServerPassword.LOGGER.warn("╚══════════════════════════════════════════════════════╝");
        return newPass;
    }

    /** 启动自动轮换任务（如已配置） */
    public void startAutoRotateIfEnabled() {
        GspConfig cfg = GspConfig.getInstance();
        if (!cfg.isAutoRotateEnabled()) return;
        cancelRotateTask();

        String mode = cfg.getAutoRotateMode();
        if ("interval".equalsIgnoreCase(mode)) {
            long interval = cfg.getAutoRotateIntervalSeconds();
            rotateTask = scheduler.scheduleAtFixedRate(
                this::rotatePassword, interval, interval, TimeUnit.SECONDS
            );
            GlobalServerPassword.LOGGER.info("[GlobalServerPassword] 密码计时轮换已启动，间隔 {}s", interval);
        } else if ("scheduled".equalsIgnoreCase(mode)) {
            scheduleDaily(cfg.getAutoRotateScheduledTime());
            GlobalServerPassword.LOGGER.info("[GlobalServerPassword] 密码定时轮换已启动，每天 {} 更换", cfg.getAutoRotateScheduledTime());
        }
    }

    private void scheduleDaily(String timeStr) {
        LocalTime target;
        try {
            target = LocalTime.parse(timeStr, TIME_FMT);
        } catch (Exception e) {
            GlobalServerPassword.LOGGER.error("[GlobalServerPassword] auto_rotate_scheduled_time 格式错误: {}", timeStr);
            return;
        }
        Runnable task = () -> {
            rotatePassword();
            // 安排明天同一时刻
            scheduleDaily(timeStr);
        };
        LocalTime now = LocalTime.now();
        long secondsUntil = now.until(target, java.time.temporal.ChronoUnit.SECONDS);
        if (secondsUntil <= 0) secondsUntil += 86400; // 已过则定为明天
        rotateTask = scheduler.schedule(task, secondsUntil, TimeUnit.SECONDS);
    }

    private void cancelRotateTask() {
        if (rotateTask != null && !rotateTask.isDone()) {
            rotateTask.cancel(false);
        }
    }

    /** 生成 8 位大小写字母 + 数字随机密码 */
    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    /** SHA-256 哈希 */
    public static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
