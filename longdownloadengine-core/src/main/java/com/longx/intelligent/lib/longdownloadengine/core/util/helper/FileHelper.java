package com.longx.intelligent.lib.longdownloadengine.core.util.helper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by LONG on 2026/9/12 at 下午4:59.
 */
public class FileHelper {

    public static File getUniqueDownloadFile(File dir, String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = new SimpleDateFormat("yyMMdd_HHmm", Locale.getDefault()).format(new Date());
        }
        File file = new File(dir, fileName);
        if (!file.exists()) {
            return file;
        }
        int dotIndex = fileName.lastIndexOf('.');
        String baseName;
        String extension;
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            baseName = fileName;
            extension = "";
        }
        int count = 1;
        while (true) {
            file = new File(dir, baseName + " " + count + extension);
            if (!file.exists()) {
                return file;
            }
            count++;
        }
    }
}
