package com.example.globalserverpassword;

import com.example.globalserverpassword.auth.IpWhitelistManager;
import com.example.globalserverpassword.config.GspConfig;
import com.example.globalserverpassword.command.GspCommand;
import com.example.globalserverpassword.event.GspServerEvents;
import com.example.globalserverpassword.manager.PasswordManager;
import com.example.globalserverpassword.manager.PlayerStateManager;
import com.example.globalserverpassword.manager.StatsManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(GlobalServerPassword.MOD_ID)
public class GlobalServerPassword {
    public static final String MOD_ID = "globalserverpassword";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public GlobalServerPassword(IEventBus modEventBus) {
        // 加载配置
        GspConfig.getInstance().load();

        // 加载IP白名单
        IpWhitelistManager.getInstance().load();

        // 加载统计数据
        StatsManager.getInstance().load();

        // 初始化密码（首次运行时自动生成）
        PasswordManager.getInstance().init();

        // 注册生命周期
        modEventBus.addListener(this::commonSetup);

        // 注册游戏事件
        NeoForge.EVENT_BUS.register(GspServerEvents.class);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[GlobalServerPassword] 模组初始化完成");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        GspCommand.register(event.getDispatcher());
    }
}
