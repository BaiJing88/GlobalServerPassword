package com.example.globalserverpassword.auth;

import com.example.globalserverpassword.config.GspConfig;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP白名单管理器
 * 记录已认证玩家的IP，相同IP再次加入时自动认证
 */
public class IpWhitelistManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(IpWhitelistManager.class);
    private static final String DATA_FILE = "config/globalserverpassword_ip_whitelist.json";
    private static IpWhitelistManager INSTANCE;

    // playerUUID -> IP记录
    private final Map<String, IpRecord> whitelist = new ConcurrentHashMap<>();
    private final Path dataPath;
    private final Gson gson;

    private IpWhitelistManager() {
        this.dataPath = Paths.get(DATA_FILE);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static IpWhitelistManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new IpWhitelistManager();
        }
        return INSTANCE;
    }

    /** 加载IP白名单 */
    public void load() {
        try {
            Files.createDirectories(dataPath.getParent());
            if (Files.exists(dataPath)) {
                String json = Files.readString(dataPath);
                Type type = new TypeToken<Map<String, IpRecord>>(){}.getType();
                Map<String, IpRecord> loaded = gson.fromJson(json, type);
                if (loaded != null) {
                    whitelist.clear();
                    whitelist.putAll(loaded);
                }
                LOGGER.info("[GlobalServerPassword] IP白名单已加载，共 {} 条记录", whitelist.size());
            }
        } catch (IOException e) {
            LOGGER.error("[GlobalServerPassword] IP白名单加载失败", e);
        }
    }

    /** 保存IP白名单 */
    public void save() {
        try {
            Files.createDirectories(dataPath.getParent());
            String json = gson.toJson(whitelist);
            Files.writeString(dataPath, json);
        } catch (IOException e) {
            LOGGER.error("[GlobalServerPassword] IP白名单保存失败", e);
        }
    }

    /**
     * 检查玩家是否应该自动认证
     * @param player 玩家
     * @return true 如果IP匹配且密码版本未变
     */
    public boolean shouldAutoAuthenticate(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        String currentIp = getPlayerIp(player);
        int currentPasswordVersion = GspConfig.getInstance().getPasswordVersion();

        IpRecord record = whitelist.get(uuid);
        if (record == null) return false;

        // 检查IP是否匹配
        if (!currentIp.equals(record.ip)) return false;

        // 检查密码版本是否一致（密码更改后需要重新认证）
        if (record.passwordVersion != currentPasswordVersion) return false;

        return true;
    }

    /**
     * 记录玩家的IP（认证成功后调用）
     * @param player 已认证的玩家
     */
    public void recordPlayerIp(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        String ip = getPlayerIp(player);
        int passwordVersion = GspConfig.getInstance().getPasswordVersion();

        whitelist.put(uuid, new IpRecord(ip, passwordVersion));
        save();
        LOGGER.debug("[GlobalServerPassword] 记录玩家IP: {} -> {}", player.getGameProfile().getName(), ip);
    }

    /** 清除所有IP记录 */
    public void clearAllRecords() {
        int count = whitelist.size();
        whitelist.clear();
        save();
        LOGGER.info("[GlobalServerPassword] 已清除 {} 条IP白名单记录", count);
    }

    /** 获取玩家IP地址 */
    private String getPlayerIp(ServerPlayer player) {
        String ip = player.connection.getRemoteAddress().toString();
        // 移除端口号，只保留IP
        if (ip.contains(":")) {
            ip = ip.substring(0, ip.lastIndexOf(":"));
        }
        // 移除开头的 /
        if (ip.startsWith("/")) {
            ip = ip.substring(1);
        }
        return ip;
    }

    /** IP记录数据类 */
    private static class IpRecord {
        String ip;
        int passwordVersion;

        IpRecord(String ip, int passwordVersion) {
            this.ip = ip;
            this.passwordVersion = passwordVersion;
        }
    }
}
