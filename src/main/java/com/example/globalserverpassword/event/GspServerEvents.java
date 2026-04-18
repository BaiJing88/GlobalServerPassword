package com.example.globalserverpassword.event;

import com.example.globalserverpassword.GlobalServerPassword;
import com.example.globalserverpassword.auth.IpWhitelistManager;
import com.example.globalserverpassword.config.GspConfig;
import com.example.globalserverpassword.manager.PlayerStateManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.common.util.TriState;

/**
 * 服务端事件监听：
 *   - 玩家加入/离开：初始化/清理状态
 *   - 每秒检查：超时踢出 + 持续施加缓慢&失明效果
 *   - 阻止未认证玩家的所有操作
 */
public class GspServerEvents {

    // ======================================================
    //  玩家加入/离开
    // ======================================================

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        // 注册玩家（内部会检查IP白名单自动认证）
        boolean autoAuth = PlayerStateManager.getInstance().onPlayerJoin(player);
        
        if (autoAuth) {
            // 自动认证成功
            player.sendSystemMessage(Component.literal("§a[全服密码] 检测到相同IP，已自动认证！"));
            GlobalServerPassword.LOGGER.info(
                "[GlobalServerPassword] 玩家 {} 通过IP白名单自动认证", player.getName().getString()
            );
        } else {
            // 需要手动认证
            player.sendSystemMessage(Component.literal(
                "§c[全服密码] §f请输入密码以继续游戏: §e/l <密码> §f或 §e/login <密码>"
            ));
            // 立即施加锁定效果
            applyLockEffects(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStateManager.getInstance().onPlayerLeave(player);
    }

    // ======================================================
    //  每服务端Tick检查（20tick=1s 检查一次）
    // ======================================================

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;

        GspConfig cfg = GspConfig.getInstance();
        var server = event.getServer();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PlayerStateManager.getInstance().isAuthenticated(player)) continue;

            // 持续施加效果（防止效果到期）
            applyLockEffects(player);

            // 超时踢出
            if (cfg.isTimeoutEnabled()) {
                long waited = PlayerStateManager.getInstance().getWaitSeconds(player);
                if (waited >= cfg.getTimeoutSeconds()) {
                    player.connection.disconnect(Component.literal(
                        "§c[全服密码] 认证超时（" + cfg.getTimeoutSeconds() + "秒内未输入密码），已断开连接。"
                    ));
                    GlobalServerPassword.LOGGER.info(
                        "[GlobalServerPassword] 玩家 {} 因超时被踢出", player.getName().getString()
                    );
                }
            }
        }
    }

    // ======================================================
    //  阻止未认证玩家操作
    // ======================================================

    /** 阻止与方块交互（右键） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerStateManager.getInstance().isAuthenticated(player)) return;
        event.setCanceled(true);
    }

    /** 阻止右键使用物品 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerStateManager.getInstance().isAuthenticated(player)) return;
        event.setCanceled(true);
    }

    /** 阻止与实体交互 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerStateManager.getInstance().isAuthenticated(player)) return;
        event.setCanceled(true);
    }

    /** 阻止攻击实体 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerStateManager.getInstance().isAuthenticated(player)) return;
        event.setCanceled(true);
    }

    /** 阻止左键点击方块（挖掘） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerStateManager.getInstance().isAuthenticated(player)) return;
        event.setCanceled(true);
    }

    /** 阻止捡起物品（Pre 阶段设置为不可捡） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (PlayerStateManager.getInstance().isAuthenticated(player)) return;
        event.setCanPickup(TriState.FALSE);
    }

    /** 阻止聊天（登录命令仍可用） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        if (PlayerStateManager.getInstance().isAuthenticated(event.getPlayer())) return;
        event.setCanceled(true);
        event.getPlayer().sendSystemMessage(Component.literal(
            "§c[全服密码] 请先输入密码: §e/l <密码>"
        ));
    }

    /** 阻止打开容器 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerStateManager.getInstance().isAuthenticated(player)) return;
        
        // 检查是否是物品栏（InventoryMenu）
        if (event.getContainer() instanceof InventoryMenu) {
            player.closeContainer();
            player.sendSystemMessage(Component.literal("§c[全服密码] 请先认证才能打开背包！"));
            return;
        }
        
        player.closeContainer();
        player.sendSystemMessage(Component.literal("§c[全服密码] 请先认证才能打开容器！"));
    }

    /** 阻止丢弃物品 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (PlayerStateManager.getInstance().isAuthenticated(player)) return;
        
        // 取消事件
        event.setCanceled(true);
        
        // 获取物品实体并返还给玩家
        // ItemTossEvent.getEntity() 返回 ItemEntity，然后 getItem() 获取 ItemStack
        var itemEntity = event.getEntity();
        if (itemEntity != null) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                // 尝试把物品放回玩家背包
                if (!player.getInventory().add(stack)) {
                    // 背包满了就掉在玩家脚下（理论上认证前背包不会满，因为捡不了东西）
                    player.drop(stack, false);
                }
            }
        }
        player.sendSystemMessage(Component.literal("§c[全服密码] 请先认证才能丢弃物品！"));
    }

    /** 每tick强制冻结未认证玩家位置（防止跳跃/移动） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerStateManager.getInstance().isAuthenticated(player)) return;
        
        // 强制设置速度为0，阻止任何移动和跳跃
        player.setDeltaMovement(0, 0, 0);
        // 传送回当前位置（防止客户端预测移动）
        player.teleportTo(player.getX(), player.getY(), player.getZ());
    }

    // ======================================================
    //  工具方法
    // ======================================================

    /**
     * 给玩家施加锁定效果：
     *   缓慢 255（速度接近0） + 失明
     *   持续时间 60tick = 3秒，每秒刷新，实际永久存在直到解锁
     */
    public static void applyLockEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
            MobEffects.MOVEMENT_SLOWDOWN, 60, 255, false, false, false
        ));
        player.addEffect(new MobEffectInstance(
            MobEffects.BLINDNESS, 60, 0, false, false, false
        ));
    }

    /** 移除锁定效果（认证成功后调用） */
    public static void removeLockEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.BLINDNESS);
    }
}
