package com.longx.intelligent.lib.longdownloadengine.android.io;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;

import com.longx.intelligent.lib.longdownloadengine.core.io.RandomAccessOutput;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by LONG on 2026/9/8 at 04:18.
 */
public class AndroidRandomAccessOutput implements RandomAccessOutput {
    private final ParcelFileDescriptor parcelFileDescriptor;
    private final FileOutputStream fos;

    public AndroidRandomAccessOutput(ParcelFileDescriptor parcelFileDescriptor) {
        this.parcelFileDescriptor = parcelFileDescriptor;
        this.fos = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());
    }

    @Override
    public void seek(long offset) throws IOException {
        try {
            Os.lseek(parcelFileDescriptor.getFileDescriptor(), offset, OsConstants.SEEK_SET);
        } catch (Exception e) {
            throw new IOException("Failed to seek to offset: " + offset, e);
        }
    }

    @Override
    public void setLength(long length) throws IOException {
        try {
            Os.ftruncate(parcelFileDescriptor.getFileDescriptor(), length);
        } catch (Exception e) {
            throw new IOException("Failed to set length: " + length, e);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        fos.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        try {
            fos.close();
        } finally {
            if (parcelFileDescriptor != null) {
                parcelFileDescriptor.close();
            }
        }
    }
}