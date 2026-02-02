package com.xiaojiang.clientupdater;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
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
        
        // 校验 mods
        validateList(update.mods_list, "./mods", "mods");
        
        // 校验 config
        validateList(update.config_list, "./config", "config");
    }

    private void validateList(java.util.List<String> list, String path, String type) {
        if (list == null || list.isEmpty()) return;
        
        File dir = new File(path);
        File[] files = dir.listFiles();
        Map<String, File> fileMap = new HashMap<>();
        
        // 建立 MD5 -> File 映射，方便查找
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    fileMap.put(Tools.getMD5(file.getPath()), file);
                }
            }
        }

        int totalCount = list.size();
        int checkedCount = 0;

        for (String md5 : list) {
            // 更新校验进度
            checkedCount++;
            // 只有在非修复状态下才更新主进度，避免闪烁
            if (!this.currentStep.startsWith("校验失败")) {
                this.currentProgress = (int) ((float) checkedCount / totalCount * 100);
            }

            // 如果文件不存在或 MD5 不匹配（即在 map 中找不到该 MD5 对应的文件）
            // 注意：这里我们的逻辑是，文件名可能不同，但 MD5 必须匹配。
            // 但如果之前下载时文件名是确定的，我们也可以直接根据下载后的文件名校验。
            // 不过 Updater 之前的逻辑是用 MD5 作为 key 来判断是否已存在的。
            // 这里最简单的方式是：重新扫描目录，看是否包含该 MD5 的文件。
            
            boolean exists = false;
            // 重新扫描目录确保最新状态
            files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        if (Tools.getMD5(file.getPath()).equalsIgnoreCase(md5)) {
                            exists = true;
                            break;
                        }
                    }
                }
            }
            
            if (!exists) {
                LOGGER.warn("文件校验失败 (MD5: " + md5 + ")，尝试重新下载...");
                this.currentStep = "校验失败，正在修复: " + md5;
                
                // 尝试重新下载，最多重试 3 次
                boolean success = false;
                for (int i = 0; i < 3; i++) {
                    this.currentProgress = 0;
                    Tools.downloadByUrl(server_url + "api/download/" + md5, path, (progress) -> {
                        this.currentProgress = progress;
                    });
                    
                    // 下载后再次校验
                    String newMD5 = "";
                    // 这里的下载逻辑 Tools.downloadByUrl 会返回文件名，我们可以根据文件名去获取文件计算 MD5
                    // 但 Tools.downloadByUrl 是同步阻塞的，所以执行到这里时文件应该已经下载好了。
                    // 重新全扫一遍虽然笨，但稳妥。
                    File[] newFiles = dir.listFiles();
                    if (newFiles != null) {
                        for (File file : newFiles) {
                            if (file.isFile() && Tools.getMD5(file.getPath()).equalsIgnoreCase(md5)) {
                                newMD5 = md5;
                                break;
                            }
                        }
                    }
                    
                    if (newMD5.equalsIgnoreCase(md5)) {
                        success = true;
                        LOGGER.info("修复成功: " + md5);
                        break;
                    } else {
                        LOGGER.warn("修复失败 (第 " + (i + 1) + " 次尝试)");
                    }
                }
                
                if (!success) {
                    this.hasError = true;
                    this.currentStep = "错误：文件修复失败，请检查网络！";
                    LOGGER.error("文件最终校验失败: " + md5);
                    // 可以选择 break 停止，或者继续尝试修复其他文件
                } else {
                    // 修复成功后，恢复显示校验状态
                    this.currentStep = "正在校验文件完整性...";
                }
            }
        }
    }
}
