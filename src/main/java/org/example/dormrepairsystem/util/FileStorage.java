package org.example.dormrepairsystem.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 图片存储工具类
 *
 * 把图片保存到项目下的本地目录（默认 uploads/），并通过 /uploads/** 静态资源映射对外访问。
 * 这样不需要任何云存储账号，克隆下来就能直接跑。
 */
@Component
@Slf4j
public class FileStorage {

    @Value("${file.storage.location:uploads}")
    private String location;

    @Value("${file.storage.url-prefix:/uploads}")
    private String urlPrefix;

    /**
     * 保存图片，返回可以直接放进 &lt;img src&gt; 的访问路径
     *
     * @param file   上传的文件
     * @param folder 子目录，例如 repair-orders
     * @return 形如 /uploads/repair-orders/20240101120000_xxx.png 的访问路径
     */
    public String uploadImage(MultipartFile file, String folder) throws IOException {
        String fileName = generateFileName(file.getOriginalFilename());
        Path directory = Paths.get(location, folder).toAbsolutePath().normalize();
        Files.createDirectories(directory);

        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw new IOException("非法的文件名：" + fileName);
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return buildAccessUrl(folder, fileName);
    }

    /**
     * 删除已保存的图片
     *
     * 无法从访问路径解析出文件时只记日志，不抛异常影响业务主流程
     */
    public void deleteImage(String imageUrl) {
        String relativePath = resolveRelativePath(imageUrl);
        if (!StringUtils.hasText(relativePath)) {
            log.warn("无法从访问路径中解析出本地文件，跳过删除：{}", imageUrl);
            return;
        }

        try {
            Path base = Paths.get(location).toAbsolutePath().normalize();
            Path target = base.resolve(relativePath).normalize();
            // 防御目录穿越，只允许删除存储目录内的文件
            if (!target.startsWith(base)) {
                log.warn("拒绝删除存储目录之外的文件：{}", imageUrl);
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("删除本地图片失败：{}，原因：{}", imageUrl, e.getMessage());
        }
    }

    private String buildAccessUrl(String folder, String fileName) {
        String prefix = trimTrailingSlash(urlPrefix);
        String subFolder = trimSlashes(folder);
        return subFolder.isEmpty() ? prefix + "/" + fileName : prefix + "/" + subFolder + "/" + fileName;
    }

    private String resolveRelativePath(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        String prefix = trimTrailingSlash(urlPrefix);
        String url = imageUrl.trim();
        if (!url.startsWith(prefix + "/")) {
            return null;
        }
        return url.substring(prefix.length() + 1);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String trimSlashes(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 生成唯一文件名：去掉路径部分，避免把 ../ 之类的名字写进存储目录
     */
    private String generateFileName(String originalFilename) {
        String safeName = StringUtils.hasText(originalFilename) ? originalFilename : "image";

        int slash = Math.max(safeName.lastIndexOf('/'), safeName.lastIndexOf('\\'));
        if (slash >= 0) {
            safeName = safeName.substring(slash + 1);
        }

        String suffix = "";
        int dot = safeName.lastIndexOf('.');
        if (dot > 0 && dot < safeName.length() - 1) {
            suffix = safeName.substring(dot).toLowerCase();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return timestamp + "_" + uuid + suffix;
    }
}
