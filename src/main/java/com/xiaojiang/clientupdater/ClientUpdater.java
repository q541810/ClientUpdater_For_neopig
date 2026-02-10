package com.xiaojiang.clientupdater;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.xiaojiang.clientupdater.screens.UpdateLogScreen;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ClientUpdater.MODID)
public class ClientUpdater {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "clientupdater_for_neopig";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean needshow = true;

    public ClientUpdater() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // 注册配置文件
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC, "clientupdater-client.toml");

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("Made By XiaoJiang");
    }

    // 客户端
    @SubscribeEvent
    public void showUpdateMassage(ScreenEvent.Opening event) {
        // 更新逻辑
        if (needshow) {
            needshow = false;
            Update update = Update.loadJsonFromURL(Config.serverAddress + "api/getupdate");
            if (update == null) {
                update = new Update();
                update.update_time = I18n.get("gui.clientupdater.error");
                update.update_logs = I18n.get("gui.clientupdater.unknowhost");
                update.mods_list = null;
                update.config_list = null;
                event.setNewScreen(new UpdateLogScreen(Config.serverAddress, update, false, Collections.emptyList()));
                LOGGER.warn("Connect Error");
            } else {
                List<String> modsList = update.mods_list == null ? Collections.emptyList() : update.mods_list;
                List<String> configList = update.config_list == null ? Collections.emptyList() : update.config_list;

                File modsDir = new File("./mods");
                File configDir = new File("./config");
                SnapshotInfo snapshotInfo = computeLocalSnapshotInfo(modsDir, configDir);
                boolean canSkipMd5 = update.update_time != null
                        && update.update_time.equals(Config.last_update_time)
                        && !snapshotInfo.snapshot.isEmpty()
                        && snapshotInfo.snapshot.equals(Config.last_local_snapshot)
                        && Config.last_files_match;
                if (canSkipMd5) {
                    return;
                }

                boolean needupdate = false;
                if (configList.size() > snapshotInfo.configFileCount) {
                    needupdate = true;
                }
                List<String> localExtraModNames = computeLocalExtraModNames(modsList);
                if (needupdate) {
                    Config.setLastLocalSnapshot(snapshotInfo.snapshot);
                    Config.setLastFilesMatch(false);
                    event.setNewScreen(new UpdateLogScreen(Config.serverAddress, update, true, localExtraModNames));
                    Config.setLastUpdateTime(update.update_time);
                    return;
                }

                Map<String, String> localMods = computeMd5ToNameMap(new File("./mods"), false);
                Set<String> localModMd5Set = localMods.keySet();
                Set<String> expectedModMd5Set = new HashSet<>(modsList);

                Set<String> expectedConfigMd5Set = new HashSet<>(configList);
                Set<String> localConfigMd5Set = computeMd5Set(new File("./config"));

                for (String md5 : expectedModMd5Set) {
                    if (!localModMd5Set.contains(md5)) {
                        needupdate = true;
                    }
                }
                for (String md5 : localModMd5Set) {
                    if (!expectedModMd5Set.contains(md5)) {
                        String fileName = localMods.get(md5);
                        if (!Config.isModWhitelisted(fileName)) {
                            needupdate = true;
                        }
                    }
                }
                for (String md5 : expectedConfigMd5Set) {
                    if (!localConfigMd5Set.contains(md5)) {
                        needupdate = true;
                    }
                }
                Config.setLastLocalSnapshot(snapshotInfo.snapshot);
                Config.setLastFilesMatch(!needupdate);
                // 更新判断
                if (needupdate) {
                    event.setNewScreen(new UpdateLogScreen(Config.serverAddress, update, needupdate, localExtraModNames));
                    Config.setLastUpdateTime(update.update_time);
                } else if (!update.update_time.equals(Config.last_update_time)) {
                    event.setNewScreen(new UpdateLogScreen(Config.serverAddress, update, needupdate, localExtraModNames));
                    Config.setLastUpdateTime(update.update_time);
                }
            }
        }
    }

    private static List<String> computeLocalExtraModNames(List<String> expectedModMd5List) {
        try {
            Map<String, String> localMods = computeMd5ToNameMap(new File("./mods"), false);
            Set<String> expected = new HashSet<>(expectedModMd5List == null ? Collections.emptyList() : expectedModMd5List);
            Set<String> names = new HashSet<>();
            for (Map.Entry<String, String> entry : localMods.entrySet()) {
                if (!expected.contains(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    names.add(entry.getValue());
                }
            }
            List<String> result = new ArrayList<>(names);
            Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to collect local extra mods", e);
            return Collections.emptyList();
        }
    }

    private static final class SnapshotInfo {
        private final String snapshot;
        private final int modFileCount;
        private final int configFileCount;

        private SnapshotInfo(String snapshot, int modFileCount, int configFileCount) {
            this.snapshot = snapshot;
            this.modFileCount = modFileCount;
            this.configFileCount = configFileCount;
        }
    }

    private static SnapshotInfo computeLocalSnapshotInfo(File modsDir, File configDir) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            List<String> entries = new ArrayList<>();

            int modCount = addSnapshotEntries(entries, modsDir, "mods", false);
            int configCount = addSnapshotEntries(entries, configDir, "config");

            Collections.sort(entries);
            for (String entry : entries) {
                md.update(entry.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            String snapshot = HexFormat.of().formatHex(md.digest());
            return new SnapshotInfo(snapshot, modCount, configCount);
        } catch (Exception e) {
            return new SnapshotInfo("", 0, 0);
        }
    }

    private static int addSnapshotEntries(List<String> entries, File dir, String prefix) {
        return addSnapshotEntries(entries, dir, prefix, true);
    }

    private static int addSnapshotEntries(List<String> entries, File dir, String prefix, boolean recursive) {
        if (dir == null || !dir.exists()) return 0;
        Path root = dir.toPath();
        List<File> files = collectFiles(dir, recursive);
        for (File file : files) {
            Path rel = root.relativize(file.toPath());
            String relStr = rel.toString().replace('\\', '/');
            entries.add(prefix + "|" + relStr + "|" + file.length() + "|" + file.lastModified());
        }
        return files.size();
    }

    private static List<File> collectFiles(File dir) {
        return collectFiles(dir, true);
    }

    private static List<File> collectFiles(File dir, boolean recursive) {
        List<File> result = new ArrayList<>();
        if (dir == null || !dir.exists()) return result;

        Deque<File> stack = new ArrayDeque<>();
        stack.push(dir);
        while (!stack.isEmpty()) {
            File current = stack.pop();
            File[] children = current.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) {
                    if (recursive) {
                        stack.push(child);
                    }
                } else if (child.isFile()) {
                    result.add(child);
                }
            }
        }
        return result;
    }

    private static Map<String, String> computeMd5ToNameMap(File dir) {
        return computeMd5ToNameMap(dir, true);
    }

    private static Map<String, String> computeMd5ToNameMap(File dir, boolean recursive) {
        List<File> files = collectFiles(dir, recursive);
        int threads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 6));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Map<String, String> map = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (File file : files) {
                futures.add(pool.submit(() -> {
                    String md5 = Tools.getMD5(file);
                    if (!md5.isEmpty()) {
                        map.put(md5, file.getName());
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
        return new HashMap<>(map);
    }

    private static Set<String> computeMd5Set(File dir) {
        return computeMd5ToNameMap(dir).keySet();
    }
    // Add the example block item to the building blocks tab

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.warn("此mod无需在服务端加载");
        LOGGER.warn("this mod don't need to load on server");
    }
}
