package com.xiaojiang.clientupdater;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Mod.EventBusSubscriber(modid = ClientUpdater.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final Logger LOGGER = LogUtils.getLogger();

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

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOD_FILENAME_WHITELIST = BUILDER
            .comment("模组白名单(按文件名识别，自动更新时不会删除)")
            .defineListAllowEmpty("mod_filename_whitelist", ArrayList::new, item -> item instanceof String);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static String serverAddress;
    public static String last_update_time;
    public static String last_local_snapshot;
    public static boolean last_files_match;
    public static Set<String> mod_filename_whitelist = new HashSet<>();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        serverAddress = SERVER_ADD.get();
        last_update_time = LAST_UPDATE_TIME.get();
        last_local_snapshot = LAST_LOCAL_SNAPSHOT.get();
        last_files_match = LAST_FILES_MATCH.get();
        try {
            mod_filename_whitelist = sanitizeWhitelist(MOD_FILENAME_WHITELIST.get());
        } catch (Exception e) {
            mod_filename_whitelist = new HashSet<>();
            LOGGER.warn("Failed to load mod filename whitelist, fallback to empty list", e);
        }
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

    public static Set<String> getWhitelistedModFilenames() {
        return new HashSet<>(mod_filename_whitelist);
    }

    public static boolean isModWhitelisted(String filename) {
        String normalized = normalizeFilename(filename);
        if (normalized.isEmpty()) {
            return false;
        }
        return mod_filename_whitelist.contains(normalized);
    }

    public static boolean setModWhitelist(Collection<String> filenames) {
        try {
            Set<String> normalized = sanitizeWhitelist(filenames == null
                    ? Collections.emptyList()
                    : new ArrayList<>(filenames));
            List<String> asList = new ArrayList<>(normalized);
            Collections.sort(asList);
            MOD_FILENAME_WHITELIST.set(asList);
            mod_filename_whitelist = normalized;
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to save mod filename whitelist", e);
            return false;
        }
    }

    public static String normalizeFilename(String filename) {
        if (filename == null) {
            return "";
        }
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.contains("/") || normalized.contains("\\")) {
            return "";
        }
        return normalized;
    }

    private static Set<String> sanitizeWhitelist(List<? extends String> raw) {
        Set<String> sanitized = new HashSet<>();
        if (raw == null) {
            return sanitized;
        }
        for (String value : raw) {
            String normalized = normalizeFilename(value);
            if (!normalized.isEmpty()) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

}
