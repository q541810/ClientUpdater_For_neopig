package com.xiaojiang.clientupdater;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public class Updater extends Thread {
    private static final Logger LOGGER = LogUtils.getLogger();
    public Boolean isComplete = false;
    public String server_url = "";
    public Update update = new Update();
    
    // 用于UI显示的进度信息
    public volatile String currentFileName = "";
    public volatile int currentProgress = 0;
    // 当前操作阶段描述
    public volatile String currentStep = "";
    // 校验失败标志
    public boolean hasError = false;

    public void run() {
        this.currentStep = "正在初始化...";
        Map<String, String> file_list = new HashMap<String, String>();
        File file_dir = new File("./mods");
        File files[] = file_dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file_list.put(Tools.getMD5(file.getPath()), file.getName());
                }
            }
        }
        for (String key : file_list.keySet()) {// 删除mod
            if (update.mods_list.indexOf(key) == -1) {
                File mod = new File("./mods/" + file_list.get(key));
                if (!mod.delete())
                    LOGGER.error("Can't delete the mod!");
            }
        }
        for (String key : update.mods_list) {// 下载mod
            if (file_list.get(key) == null) {
                LOGGER.info("download: " + key);
                // 更新当前下载的文件名
                this.currentFileName = key;
                this.currentStep = "正在下载: " + key;
                this.currentProgress = 0;
                Tools.downloadByUrl(server_url + "api/download/" + key, "./mods", (progress) -> {
                    this.currentProgress = progress;
                });
            }
        }
        file_dir = new File("./config");
        files = file_dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file_list.put(Tools.getMD5(file.getPath()), file.getName());
                }
            }
        }
        for (String key : update.config_list) {// 下载config
            if (file_list.get(key) == null) {
                this.currentFileName = key;
                this.currentStep = "正在下载: " + key + " 注：该名称为文件的md5值，不是文件名";
                this.currentProgress = 0;
                Tools.downloadByUrl(server_url + "api/download/" + key,
                        "./config", (progress) -> {
                            this.currentProgress = progress;
                        });
            }
        }
        
        // 校验文件完整性
        validateFiles();
        
        if (!hasError) {
            isComplete = true;
        }
    }

    private void validateFiles() {
        this.currentStep = "正在校验文件完整性...";
        this.currentProgress = 0;

        File modsDir = new File("./mods");
        File configDir = new File("./config");

        List<File> modFiles = collectFiles(modsDir);
        List<File> configFiles = collectFiles(configDir);

        int totalFiles = modFiles.size() + configFiles.size();
        AtomicInteger hashedCount = new AtomicInteger(0);

        Set<String> modMd5Set = ConcurrentHashMap.newKeySet();
        Set<String> configMd5Set = ConcurrentHashMap.newKeySet();

        int threads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 6));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (File file : modFiles) {
                futures.add(pool.submit(() -> {
                    String md5 = Tools.getMD5(file);
                    if (!md5.isEmpty()) {
                        modMd5Set.add(md5);
                    }
                    int finished = hashedCount.incrementAndGet();
                    if (!this.currentStep.startsWith("校验失败")) {
                        this.currentProgress = totalFiles == 0 ? 100 : (int) (finished * 100.0 / totalFiles);
                    }
                }));
            }

            for (File file : configFiles) {
                futures.add(pool.submit(() -> {
                    String md5 = Tools.getMD5(file);
                    if (!md5.isEmpty()) {
                        configMd5Set.add(md5);
                    }
                    int finished = hashedCount.incrementAndGet();
                    if (!this.currentStep.startsWith("校验失败")) {
                        this.currentProgress = totalFiles == 0 ? 100 : (int) (finished * 100.0 / totalFiles);
                    }
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            this.hasError = true;
            this.currentStep = "错误：文件校验异常，请重试！";
            LOGGER.error("校验阶段出现异常", e);
            return;
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        validateExpected(update.mods_list, "./mods", modMd5Set);
        if (this.hasError) return;

        validateExpected(update.config_list, "./config", configMd5Set);
        if (this.hasError) return;

        this.currentProgress = 100;
    }

    private void validateExpected(List<String> expectedMd5List, String savePath, Set<String> localMd5Set) {
        if (expectedMd5List == null || expectedMd5List.isEmpty()) return;

        for (String expectedMd5 : expectedMd5List) {
            if (localMd5Set.contains(expectedMd5)) continue;

            boolean repaired = repairAndVerify(expectedMd5, savePath, localMd5Set);
            if (!repaired) {
                this.hasError = true;
                this.currentStep = "错误：文件修复失败，请检查网络！";
                LOGGER.error("文件最终校验失败: " + expectedMd5);
                return;
            }
        }
    }

    private boolean repairAndVerify(String expectedMd5, String savePath, Set<String> localMd5Set) {
        LOGGER.warn("文件校验失败 (MD5: " + expectedMd5 + ")，尝试重新下载...");
        this.currentStep = "校验失败，正在修复: " + expectedMd5;

        for (int i = 0; i < 3; i++) {
            this.currentProgress = 0;
            String relativePath = Tools.downloadByUrl(server_url + "api/download/" + expectedMd5, savePath, (progress) -> {
                this.currentProgress = progress;
            });

            if (relativePath == null || relativePath.isEmpty()) {
                continue;
            }

            File downloadedFile = new File(savePath, relativePath.replace('/', File.separatorChar));
            String actualMd5 = Tools.getMD5(downloadedFile);
            if (actualMd5.equalsIgnoreCase(expectedMd5)) {
                localMd5Set.add(expectedMd5);
                LOGGER.info("修复成功: " + expectedMd5);
                this.currentStep = "正在校验文件完整性...";
                return true;
            }
        }

        return false;
    }

    private List<File> collectFiles(File dir) {
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
                    stack.push(child);
                } else if (child.isFile()) {
                    result.add(child);
                }
            }
        }
        return result;
    }
}
