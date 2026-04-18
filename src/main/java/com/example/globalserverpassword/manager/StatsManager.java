package com.example.globalserverpassword.manager;

import com.example.globalserverpassword.GlobalServerPassword;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据统计管理器
 * 持久化存储玩家登录统计信息
 */
public class StatsManager {
    private static StatsManager INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STATS_FILE = "config/globalserverpassword_stats.json";

    /** 玩家 UUID -> 统计信息 */
    private final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();

    private StatsManager() {}

    public static StatsManager getInstance() {
        if (INSTANCE == null) INSTANCE = new StatsManager();
        return INSTANCE;
    }

    /**
     * 玩家统计信息
     */
    public static class PlayerStats {
        /** 玩家名称 */
        public String playerName = "";
        /** 总登录次数 */
        public int totalLogins = 0;
        /** 密码错误次数 */
        public int wrongAttempts = 0;
        /** IP白名单自动认证次数 */
        public int autoAuthCount = 0;
        /** 手动输入密码认证次数 */
        public int manualAuthCount = 0;
        /** 首次登录时间戳 */
        public long firstLoginTime = 0;
        /** 最后登录时间戳 */
        public long lastLoginTime = 0;
        /** 最后登录IP */
        public String lastIp = "";
    }

    /** 加载统计数据 */
    public void load() {
        File file = new File(STATS_FILE);
        if (!file.exists()) {
            GlobalServerPassword.LOGGER.info("[GlobalServerPassword] 统计数据文件不存在，将创建新的");
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Map<String, PlayerStats> loaded = GSON.fromJson(reader, new TypeToken<Map<String, PlayerStats>>(){}.getType());
            if (loaded != null) {
                loaded.forEach((uuidStr, stat) -> {
                    try {
                        stats.put(UUID.fromString(uuidStr), stat);
                    } catch (IllegalArgumentException e) {
                        GlobalServerPassword.LOGGER.warn("[GlobalServerPassword] 无效的UUID格式: {}", uuidStr);
                    }
                });
            }
            GlobalServerPassword.LOGGER.info("[GlobalServerPassword] 已加载 {} 条玩家统计记录", stats.size());
        } catch (IOException e) {
            GlobalServerPassword.LOGGER.error("[GlobalServerPassword] 加载统计数据失败", e);
        }
    }

    /** 保存统计数据 */
    public void save() {
        try {
            File file = new File(STATS_FILE);
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                Map<String, PlayerStats> toSave = new ConcurrentHashMap<>();
                stats.forEach((uuid, stat) -> toSave.put(uuid.toString(), stat));
                GSON.toJson(toSave, writer);
            }
            GlobalServerPassword.LOGGER.debug("[GlobalServerPassword] 统计数据已保存");
        } catch (IOException e) {
            GlobalServerPassword.LOGGER.error("[GlobalServerPassword] 保存统计数据失败", e);
        }
    }

    /** 记录玩家登录（每次进服调用） */
    public void recordLogin(UUID playerId, String playerName, String ip) {
        PlayerStats stat = stats.computeIfAbsent(playerId, k -> new PlayerStats());
        stat.playerName = playerName;
        stat.totalLogins++;
        stat.lastLoginTime = System.currentTimeMillis();
        stat.lastIp = ip;
        if (stat.firstLoginTime == 0) {
            stat.firstLoginTime = stat.lastLoginTime;
        }
        save();
    }

    /** 记录密码错误 */
    public void recordWrongAttempt(UUID playerId, String playerName) {
        PlayerStats stat = stats.computeIfAbsent(playerId, k -> new PlayerStats());
        stat.playerName = playerName;
        stat.wrongAttempts++;
        save();
    }

    /** 记录自动认证成功 */
    public void recordAutoAuth(UUID playerId, String playerName) {
        PlayerStats stat = stats.computeIfAbsent(playerId, k -> new PlayerStats());
        stat.playerName = playerName;
        stat.autoAuthCount++;
        save();
    }

    /** 记录手动认证成功 */
    public void recordManualAuth(UUID playerId, String playerName) {
        PlayerStats stat = stats.computeIfAbsent(playerId, k -> new PlayerStats());
        stat.playerName = playerName;
        stat.manualAuthCount++;
        save();
    }

    /** 获取指定玩家的统计 */
    public PlayerStats getStats(UUID playerId) {
        return stats.get(playerId);
    }

    /** 获取所有统计 */
    public Map<UUID, PlayerStats> getAllStats() {
        return new ConcurrentHashMap<>(stats);
    }

    /** 获取总登录次数 */
    public long getTotalLogins() {
        return stats.values().stream().mapToLong(s -> s.totalLogins).sum();
    }

    /** 获取总错误次数 */
    public long getTotalWrongAttempts() {
        return stats.values().stream().mapToLong(s -> s.wrongAttempts).sum();
    }

    /** 获取总自动认证次数 */
    public long getTotalAutoAuths() {
        return stats.values().stream().mapToLong(s -> s.autoAuthCount).sum();
    }

    /** 获取唯一玩家数 */
    public int getUniquePlayerCount() {
        return stats.size();
    }

    /** 清除所有统计数据 */
    public void clearAll() {
        stats.clear();
        save();
        GlobalServerPassword.LOGGER.info("[GlobalServerPassword] 所有统计数据已清除");
    }
}
