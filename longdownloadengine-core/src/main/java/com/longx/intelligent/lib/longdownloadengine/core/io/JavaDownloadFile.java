package com.longx.intelligent.lib.longdownloadengine.core.io;

import com.longx.intelligent.lib.longdownloadengine.core.util.FileUtil;
import com.longx.intelligent.lib.longdownloadengine.core.util.helper.FileHelper;

import java.io.File;
import java.io.IOException;

/**
 * Created by LONG on 2026/9/8 at 00:49.
 */
public class JavaDownloadFile implements DownloadFile {
    private File file;

    public JavaDownloadFile() {
    }

    public JavaDownloadFile(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public long length() {
        return file != null ? file.length() : 0;
    }

    @Override
    public DownloadFile generateUniqueFile(String fileName) {
        if (file.isDirectory()) {
            return new JavaDownloadFile(FileHelper.getUniqueDownloadFile(file, fileName));
        } else {
            return new JavaDownloadFile(FileHelper.getUniqueDownloadFile(file.getParentFile(), file.getName()));
        }
    }

    @Override
    public void createDirs() {
        FileUtil.createParentDirs(file);
    }

    @Override
    public void createNewFile() throws IOException {
        if (!file.exists()) file.createNewFile();
    }

    @Override
    public RandomAccessOutput openForWrite() throws IOException {
        return new JavaRandomAccessOutput(file);
    }

    public void setFile(File file) {
        this.file = file;
    }
}