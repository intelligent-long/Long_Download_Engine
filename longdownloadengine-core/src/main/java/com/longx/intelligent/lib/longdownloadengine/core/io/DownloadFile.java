package com.longx.intelligent.lib.longdownloadengine.core.io;

import java.io.IOException;

/**
 * Created by LONG on 2026/9/8 at 00:47.
 */
public interface DownloadFile {
    boolean exists();
    boolean isDirectory();
    String getName();
    long length();
    DownloadFile generateUniqueFile(String fileName);
    void createDirs();
    void createNewFile() throws IOException;
    RandomAccessOutput openForWrite() throws IOException;
}
