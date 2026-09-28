package com.longx.intelligent.lib.longdownloadengine.core.io;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by LONG on 2026/9/8 at 00:48.
 */
public interface RandomAccessOutput extends Closeable {
    void seek(long offset) throws IOException;
    void setLength(long length) throws IOException;
    void write(byte[] b, int off, int len) throws IOException;
}
