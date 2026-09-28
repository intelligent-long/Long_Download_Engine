package com.longx.intelligent.lib.longdownloadengine.core.io;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by LONG on 2026/9/8 at 00:50.
 */
public class JavaRandomAccessOutput implements RandomAccessOutput {
    private final RandomAccessFile raf;

    public JavaRandomAccessOutput(File file) throws IOException {
        this.raf = new RandomAccessFile(file, "rw");
    }

    @Override
    public void seek(long offset) throws IOException {
        raf.seek(offset);
    }

    @Override
    public void setLength(long length) throws IOException {
        raf.setLength(length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        raf.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}