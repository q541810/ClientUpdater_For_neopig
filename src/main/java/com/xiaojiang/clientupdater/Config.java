package com.xiaojiang.clientupdater;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = ClientUpdater.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.ConfigValue<String> SERVER_ADD = BUILDER
            .comment("同步服务器url")
            .define("server_address", "http://example.com:25564/");

    private static final ForgeConfigSpec.ConfigValue<String> LAST_UPDATE_TIME = BUILDER
            .comment("最后更新时间(自动生成请勿更改)")
            .define("last_update_time", "0");

    private static final ForgeConfigSpec.ConfigValue<String> LAST_LOCAL_SNAPSHOT = BUILDER
            .comment("本地文件快照(自动生成请勿更改)")
            .define("last_local_snapshot", "");

    private static final ForgeConfigSpec.ConfigValue<Boolean> LAST_FILES_MATCH = BUILDER
            .comment("上次校验是否一致(自动生成请勿更改)")
            .define("last_files_match", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static String serverAddress;
    public static String last_update_time;
    public static String last_local_snapshot;
    public static boolean last_files_match;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        serverAddress = SERVER_ADD.get();
        last_update_time = LAST_UPDATE_TIME.get();
        last_local_snapshot = LAST_LOCAL_SNAPSHOT.get();
        last_files_match = LAST_FILES_MATCH.get();
    }

    static void setLastUpdateTime(String time) {
        LAST_UPDATE_TIME.set(time);
    }

    static void setLastLocalSnapshot(String snapshot) {
        LAST_LOCAL_SNAPSHOT.set(snapshot == null ? "" : snapshot);
    }

    static void setLastFilesMatch(boolean match) {
        LAST_FILES_MATCH.set(match);
    }

}
