package com.longx.intelligent.lib.longdownloadengine.core.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by LONG on 2026/9/6 at 03:20.
 */
public class FileUtil {
    public static void createParentDirs(File file) {
        if (file != null) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        }
    }
}
