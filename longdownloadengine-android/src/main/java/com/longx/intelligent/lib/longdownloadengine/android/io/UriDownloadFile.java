package com.longx.intelligent.lib.longdownloadengine.android.io;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.documentfile.provider.DocumentFile;

import com.longx.intelligent.lib.longdownloadengine.core.io.DownloadFile;
import com.longx.intelligent.lib.longdownloadengine.core.io.RandomAccessOutput;

import java.io.IOException;

/**
 * Created by LONG on 2026/9/8 at 上午7:03.
 */
public class UriDownloadFile implements DownloadFile {
    private Context context;
    private Uri uri;
    private boolean isTreeUri;

    public UriDownloadFile() {
    }

    public UriDownloadFile(Context context, Uri uri, boolean isTreeUri) {
        this.context = context.getApplicationContext();
        this.uri = uri;
        this.isTreeUri = isTreeUri;
    }

    public Uri getUri() {
        return uri;
    }

    @Override
    public boolean exists() {
        DocumentFile documentFile = isTreeUri ? DocumentFile.fromTreeUri(context, uri) : DocumentFile.fromSingleUri(context, uri);
        return documentFile != null && documentFile.exists();
    }

    @Override
    public boolean isDirectory() {
        DocumentFile documentFile = isTreeUri ? DocumentFile.fromTreeUri(context, uri) : DocumentFile.fromSingleUri(context, uri);
        return documentFile != null && documentFile.isDirectory();
    }

    @Override
    public String getName() {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index != -1) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }
        DocumentFile documentFile = isTreeUri ? DocumentFile.fromTreeUri(context, uri) : DocumentFile.fromSingleUri(context, uri);
        if (documentFile != null && documentFile.getName() != null) {
            return documentFile.getName();
        }
        return uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "unknown_file";
    }

    @Override
    public long length() {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index != -1) {
                    return cursor.getLong(index);
                }
            }
        } catch (Exception ignored) {
        }
        DocumentFile documentFile = isTreeUri ? DocumentFile.fromTreeUri(context, uri) : DocumentFile.fromSingleUri(context, uri);
        if (documentFile != null && documentFile.exists()) {
            return documentFile.length();
        }
        return 0;
    }

    @Override
    public DownloadFile generateUniqueFile(String fileName) {
        if (!isDirectory()) {
            return this;
        }
        DocumentFile parentDocumentFile = DocumentFile.fromTreeUri(context, uri);
        if (parentDocumentFile != null) {
            DocumentFile existingFile = parentDocumentFile.findFile(fileName);
            if (existingFile == null) {
                DocumentFile newFile = parentDocumentFile.createFile("*/*", fileName);
                if (newFile != null) {
                    return new UriDownloadFile(context, newFile.getUri(), false);
                }
            } else {
                String newFileName = generateUniqueName(parentDocumentFile, fileName);
                DocumentFile newFile = parentDocumentFile.createFile("*/*", newFileName);
                if (newFile != null) {
                    return new UriDownloadFile(context, newFile.getUri(), false);
                }
            }
        }
        throw new IllegalStateException("Failed to generate unique file in uri: " + uri);
    }

    private String generateUniqueName(DocumentFile parentDir, String originalName) {
        String baseName = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf(".");
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }
        int counter = 1;
        String candidateName = originalName;
        while (parentDir.findFile(candidateName) != null) {
            candidateName = baseName + "(" + counter + ")" + extension;
            counter++;
        }
        return candidateName;
    }

    @Override
    public void createDirs() {
    }

    @Override
    public void createNewFile() throws IOException {
        if (!exists()) {
            throw new IOException("Uri file does not exist, and cannot implicitly create single uri without Tree Document: " + uri);
        }
    }

    @Override
    public RandomAccessOutput openForWrite() throws IOException {
        ParcelFileDescriptor parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "rw");
        if (parcelFileDescriptor == null) {
            throw new IOException("ContentResolver failed to open file descriptor for uri: " + uri);
        }
        return new AndroidRandomAccessOutput(parcelFileDescriptor);
    }
}