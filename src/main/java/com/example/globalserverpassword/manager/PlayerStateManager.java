package com.example.globalserverpassword.manager;

import com.example.globalserverpassword.auth.IpWhitelistManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家认证状态管理器
 * 追踪每位玩家的认证状态、错误次数、加入时间
 */

/**
 * 玩家认证状态管理器
 * 追踪每位玩家的认证状态、错误次数、加入时间
 */
public class PlayerStateManager {
    private static PlayerStateManager INSTANCE;

    /** 玩家 UUID -> 是否已认证 */
    private final Map<UUID, Boolean> authenticated = new ConcurrentHashMap<>();
    /** 玩家 UUID -> 已输错次数 */
    private final Map<UUID, Integer> wrongAttempts = new ConcurrentHashMap<>();
    /** 玩家 UUID -> 加入时间（System.currentTimeMillis()） */
    private final Map<UUID, Long> joinTime = new ConcurrentHashMap<>();

    private PlayerStateManager() {}

    public static PlayerStateManager getInstance() {
        if (INSTANCE == null) INSTANCE = new PlayerStateManager();
        return INSTANCE;
    }

    /** 玩家进服时注册，返回是否自动认证成功 */
    public boolean onPlayerJoin(ServerPlayer player) {
        UUID id = player.getUUID();
        String name = player.getName().getString();
        String ip = player.getIpAddress();
        
        // 记录登录统计
        StatsManager.getInstance().recordLogin(id, name, ip);
        
        // 检查IP白名单
        if (IpWhitelistManager.getInstance().shouldAutoAuthenticate(player)) {
            authenticated.put(id, true);
            StatsManager.getInstance().recordAutoAuth(id, name);
            return true; // 自动认证成功
        }
        
        authenticated.put(id, false);
        wrongAttempts.put(id, 0);
        joinTime.put(id, System.currentTimeMillis());
        return false; // 需要手动认证
    }

    /** 玩家离服时清理 */
    public void onPlayerLeave(ServerPlayer player) {
        UUID id = player.getUUID();
        authenticated.remove(id);
        wrongAttempts.remove(id);
        joinTime.remove(id);
    }

    public boolean isAuthenticated(ServerPlayer player) {
        return Boolean.TRUE.equals(authenticated.get(player.getUUID()));
    }

    public void setAuthenticated(ServerPlayer player) {
        UUID id = player.getUUID();
        authenticated.put(id, true);
        wrongAttempts.remove(id);
        joinTime.remove(id);
        // 记录IP到白名单
        IpWhitelistManager.getInstance().recordPlayerIp(player);
        // 记录手动认证统计
        StatsManager.getInstance().recordManualAuth(id, player.getName().getString());
    }

    /** 累计错误次数，返回当前已错误次数 */
    public int incrementWrong(ServerPlayer player) {
        UUID id = player.getUUID();
        int count = wrongAttempts.getOrDefault(id, 0) + 1;
        wrongAttempts.put(id, count);
        // 记录错误统计
        StatsManager.getInstance().recordWrongAttempt(id, player.getName().getString());
        return count;
    }

    public int getWrongAttempts(ServerPlayer player) {
        return wrongAttempts.getOrDefault(player.getUUID(), 0);
    }

    /** 返回玩家未认证时已等待的秒数，-1 表示不在等待列表 */
    public long getWaitSeconds(ServerPlayer player) {
        Long join = joinTime.get(player.getUUID());
        if (join == null) return -1;
        return (System.currentTimeMillis() - join) / 1000;
    }
}
