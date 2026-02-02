package com.xiaojiang.clientupdater;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;

public class Tools {

    @SuppressWarnings("unused")
    private static final Logger LOGGER = LogUtils.getLogger();

    public static String getMD5(String path) {
        StringBuffer sb = new StringBuffer();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(FileUtils.readFileToByteArray(new File(path)));
            byte[] b = md.digest();
            for (int i = 0; i < b.length; i++) {
                int d = b[i];
                if (d < 0) {
                    d = b[i] & 0xff;
                }
                if (d < 16) {
                    sb.append("0");
                }
                sb.append(Integer.toHexString(d));
            }
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    /**
     * 通过URL下载文件，并显示进度条
     * @param urlStr 下载链接
     * @param savePath 保存路径
     * @param progressCallback 进度回调函数，接收进度百分比(0-100)
     * @return 下载的文件名
     */
    public static String downloadByUrl(String urlStr, String savePath, Consumer<Integer> progressCallback) {
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        java.io.FileOutputStream outputStream = null;
        try {
            URL url = new URL(urlStr);
            // 建立连接
            conn = (HttpURLConnection) url.openConnection();
            conn.connect();

            // 获取文件总大小，用于计算进度
            long totalSize = conn.getContentLengthLong();

            // 获取文件名
            String fileName = "";
            // 优先从Header中获取文件名
            if (conn.getHeaderField("Path") != null) {
                fileName = conn.getHeaderField("Path");
                // 解码文件名，防止乱码
                fileName = URLDecoder.decode(URLEncoder.encode(fileName, "latin1"), "utf-8");
            } else {
                // 如果Header中没有，尝试调用现有方法或解析URL
                fileName = getFileName(urlStr);
                if (fileName == null || fileName.isEmpty()) {
                    fileName = urlStr.substring(urlStr.lastIndexOf("/") + 1);
                }
            }

            String path = "";
            // 处理文件名中包含路径的情况，创建对应子目录
            int index = fileName.lastIndexOf("/");
            if (index > -1) {
                // LOGGER.info(filepath.substring(0, index));
                path = savePath + '/' + fileName.substring(0, index);
                fileName = fileName.substring(index + 1, fileName.length());
            } else {
                path = savePath;
            }
            File saveDir = new File(path);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            // 创建文件路径
            File file = new File(saveDir, fileName);

            // 准备输入输出流
            inputStream = conn.getInputStream();
            outputStream = new java.io.FileOutputStream(file);

            byte[] buffer = new byte[4096]; // 缓冲区大小 4KB
            int bytesRead;
            long downloadedSize = 0;

            int lastProgress = -1;
            // 循环读取数据并写入文件
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                downloadedSize += bytesRead;

                // 计算并更新进度
                if (totalSize > 0) {
                    int progress = (int) (downloadedSize * 100 / totalSize);
                    // 只有进度发生变化时才回调，避免过于频繁
                    if (progress != lastProgress) {
                        if (progressCallback != null) {
                            progressCallback.accept(progress);
                        }
                        lastProgress = progress;
                    }
                }
            }
            
            // 记录日志
            LOGGER.info("下载完成: " + fileName);

            return fileName;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭流资源
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static String getFileName(String href) {
        try {
            URL url = new URL(href);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect(); // 获取文件名和扩展名
            conn.getResponseCode();
            href = conn.getURL().toString();
            // 获取header 确定文件名和扩展名，并防止乱码
            String filename = "";
            if (conn.getHeaderField("Path") != null) {
                filename = conn.getHeaderField("Path");
                filename = URLDecoder.decode(URLEncoder.encode(filename, "latin1"), "utf-8");
                // LOGGER.info(filename);
                // int index = filename.indexOf("filename*=UTF-8''");
                // if (index > -1) {
                // filename = filename.substring(index + "filename*=UTF-8''".length());
                // filename = URLDecoder.decode(filename, "UTF-8");
                // } else {
                // filename = filename.substring("attachment; filename=".length());
                // filename = URLDecoder.decode(filename, "UTF-8");
                // }
            }
            return filename;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // public static String getFilePath(String href) {
    // try {
    // URL url = new URL(href);
    // HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    // conn.connect(); // 获取文件名和扩展名
    // conn.getResponseCode();
    // // 获取header 确定文件名和扩展名，并防止乱码
    // String filepath = "";
    // filepath = conn.getHeaderField("Path");
    // if (conn.getHeaderField("Path") != null) {
    // int index = filepath.lastIndexOf("/");
    // if (index > -1) {
    // // LOGGER.info(filepath.substring(0, index));
    // return filepath.substring(0, index);
    // }
    // }
    // return "";
    // } catch (Exception e) {
    // e.printStackTrace();
    // return "";
    // }
    // }
}
