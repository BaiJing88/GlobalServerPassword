package com.example.globalserverpassword.command;

import com.example.globalserverpassword.GlobalServerPassword;
import com.example.globalserverpassword.auth.IpWhitelistManager;
import com.example.globalserverpassword.config.GspConfig;
import com.example.globalserverpassword.event.GspServerEvents;
import com.example.globalserverpassword.manager.PasswordManager;
import com.example.globalserverpassword.manager.PlayerStateManager;
import com.example.globalserverpassword.manager.StatsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * 命令注册：
 *   /l <密码>          —— 玩家登录
 *   /login <密码>      —— 玩家登录（同上）
 *   /gsp setpassword <明文密码>  —— OP设置密码（需要OP权限2级）
 */
public class GspCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /l <password>
        dispatcher.register(
            Commands.literal("l")
                .then(Commands.argument("password", StringArgumentType.word())
                    .executes(ctx -> handleLogin(ctx, StringArgumentType.getString(ctx, "password")))
                )
        );

        // /login <password>
        dispatcher.register(
            Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.word())
                    .executes(ctx -> handleLogin(ctx, StringArgumentType.getString(ctx, "password")))
                )
        );

        // /gsp setpassword <newPassword>  (OP only)
        dispatcher.register(
            Commands.literal("gsp")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("setpassword")
                    .then(Commands.argument("newPassword", StringArgumentType.word())
                        .executes(ctx -> handleSetPassword(ctx, StringArgumentType.getString(ctx, "newPassword")))
                    )
                )
                .then(Commands.literal("clearip")
                    .executes(ctx -> handleClearIp(ctx))
                )
                .then(Commands.literal("stats")
                    .executes(ctx -> handleStats(ctx))
                )
                .then(Commands.literal("clearstats")
                    .executes(ctx -> handleClearStats(ctx))
                )
        );
    }

    // ──────────────────────────────────────────────────
    //  登录处理
    // ──────────────────────────────────────────────────

    private static int handleLogin(CommandContext<CommandSourceStack> ctx, String input) {
        CommandSourceStack src = ctx.getSource();

        // 只允许玩家执行
        if (!src.isPlayer()) {
            src.sendFailure(Component.literal("[全服密码] 该命令只能由玩家使用"));
            return 0;
        }

        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }

        // 已认证则提示
        if (PlayerStateManager.getInstance().isAuthenticated(player)) {
            player.sendSystemMessage(Component.literal("§a[全服密码] 你已经认证过了！"));
            return 1;
        }

        GspConfig cfg = GspConfig.getInstance();
        boolean correct = PasswordManager.getInstance().verify(input);

        if (correct) {
            PlayerStateManager.getInstance().setAuthenticated(player);
            GspServerEvents.removeLockEffects(player);
            player.sendSystemMessage(Component.literal("§a[全服密码] §f密码正确！欢迎游玩！"));
            GlobalServerPassword.LOGGER.info("[GlobalServerPassword] 玩家 {} 认证成功", player.getName().getString());
        } else {
            int wrong = PlayerStateManager.getInstance().incrementWrong(player);
            int max = cfg.getMaxWrongAttempts();
            GlobalServerPassword.LOGGER.info("[GlobalServerPassword] 玩家 {} 输入错误密码（{}/{}）",
                player.getName().getString(), wrong, max);

            if (cfg.isWrongAttemptsEnabled() && wrong >= max) {
                player.connection.disconnect(Component.literal(
                    "§c[全服密码] 密码输入错误次数过多（" + max + "次），已断开连接。"
                ));
            } else {
                int remaining = cfg.isWrongAttemptsEnabled() ? (max - wrong) : -1;
                String hint = remaining >= 0
                    ? "§c[全服密码] §f密码错误！还剩 §e" + remaining + " §f次机会。"
                    : "§c[全服密码] §f密码错误！请重试。";
                player.sendSystemMessage(Component.literal(hint));
            }
        }
        return 1;
    }

    // ──────────────────────────────────────────────────
    //  OP 设置密码
    // ──────────────────────────────────────────────────

    private static int handleSetPassword(CommandContext<CommandSourceStack> ctx, String newPassword) {
        CommandSourceStack src = ctx.getSource();
        if (newPassword.length() < 4) {
            src.sendFailure(Component.literal("§c[全服密码] 密码长度至少4位！"));
            return 0;
        }
        PasswordManager.getInstance().setPassword(newPassword);
        // 密码更改后增加版本号，使所有IP白名单失效
        GspConfig.getInstance().incrementPasswordVersion();
        src.sendSuccess(() -> Component.literal("§a[全服密码] 密码已更新！所有玩家需要重新认证。"), true);
        GlobalServerPassword.LOGGER.info("[GlobalServerPassword] OP {} 手动更新了服务器密码",
            src.getTextName());
        return 1;
    }

    // ──────────────────────────────────────────────────
    //  OP 清除IP白名单
    // ──────────────────────────────────────────────────

    private static int handleClearIp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        IpWhitelistManager.getInstance().clearAllRecords();
        src.sendSuccess(() -> Component.literal("§a[全服密码] 已清除所有IP白名单记录，所有玩家下次加入需要重新认证！"), true);
        GlobalServerPassword.LOGGER.info("[GlobalServerPassword] OP {} 清除了IP白名单",
            src.getTextName());
        return 1;
    }

    // ──────────────────────────────────────────────────
    //  OP 查看统计
    // ──────────────────────────────────────────────────

    private static int handleStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        StatsManager stats = StatsManager.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        src.sendSuccess(() -> Component.literal("§6========== [全服密码统计] =========="), false);
        src.sendSuccess(() -> Component.literal("§f唯一玩家数: §e" + stats.getUniquePlayerCount()), false);
        src.sendSuccess(() -> Component.literal("§f总登录次数: §e" + stats.getTotalLogins()), false);
        src.sendSuccess(() -> Component.literal("§f总错误次数: §e" + stats.getTotalWrongAttempts()), false);
        src.sendSuccess(() -> Component.literal("§f自动认证次数: §e" + stats.getTotalAutoAuths()), false);
        src.sendSuccess(() -> Component.literal("§6========== [玩家详情] =========="), false);

        Map<UUID, StatsManager.PlayerStats> allStats = stats.getAllStats();
        if (allStats.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7暂无数据"), false);
        } else {
            int count = 0;
            for (Map.Entry<UUID, StatsManager.PlayerStats> entry : allStats.entrySet()) {
                if (count >= 10) {
                    src.sendSuccess(() -> Component.literal("§7... 还有 " + (allStats.size() - 10) + " 条记录"), false);
                    break;
                }
                StatsManager.PlayerStats s = entry.getValue();
                String lastLogin = s.lastLoginTime > 0 ? sdf.format(new Date(s.lastLoginTime)) : "N/A";
                src.sendSuccess(() -> Component.literal(
                    "§f" + s.playerName + ": " +
                    "§7登录§e" + s.totalLogins + "§7次, " +
                    "§7错误§c" + s.wrongAttempts + "§7次, " +
                    "§7自动§a" + s.autoAuthCount + "§7次, " +
                    "§7最后: §b" + lastLogin
                ), false);
                count++;
            }
        }

        GlobalServerPassword.LOGGER.info("[GlobalServerPassword] OP {} 查看了统计数据",
            src.getTextName());
        return 1;
    }

    // ──────────────────────────────────────────────────
    //  OP 清除统计
    // ──────────────────────────────────────────────────

    private static int handleClearStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        StatsManager.getInstance().clearAll();
        src.sendSuccess(() -> Component.literal("§a[全服密码] 已清除所有统计数据！"), true);
        GlobalServerPassword.LOGGER.info("[GlobalServerPassword] OP {} 清除了统计数据",
            src.getTextName());
        return 1;
    }
}
