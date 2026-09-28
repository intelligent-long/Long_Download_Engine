package com.longx.intelligent.lib.longdownloadengine.core;

import com.longx.intelligent.lib.longdownloadengine.core.client.HttpClient;
import com.longx.intelligent.lib.longdownloadengine.core.downloader.Downloader;
import com.longx.intelligent.lib.longdownloadengine.core.downloader.PartDownloader;
import com.longx.intelligent.lib.longdownloadengine.core.io.DownloadFile;
import com.longx.intelligent.lib.longdownloadengine.core.task.MultiThreadDownloadTask;
import com.longx.intelligent.lib.longdownloadengine.core.task.SingleThreadDownloadTask;

import java.io.File;

/**
 * Created by LONG on 2026/9/6 at 14:22.
 */
public class LongDownloadEngine {
    public static Downloader newDownloader(String url, DownloadFile destFile){
        return new Downloader(url, destFile);
    }
    public static Downloader newDownloader(String url, File destFile){
        return new Downloader(url, destFile);
    }
    public static Downloader newDownloader(String url, String destFilePath){
        return new Downloader(url, destFilePath);
    }

    public static PartDownloader newPartDownloader(String url, DownloadFile destFile, long startOffset, long endOffset){
        return new PartDownloader(url, destFile, startOffset, endOffset);
    }

    public static PartDownloader newPartDownloader(String url, File destFile, long startOffset, long endOffset){
        return new PartDownloader(url, destFile, startOffset, endOffset);
    }

    public static PartDownloader newPartDownloader(String url, String destFilePath, long startOffset, long endOffset){
        return new PartDownloader(url, destFilePath, startOffset, endOffset);
    }

    public static SingleThreadDownloadTask newSingleThreadDownloadTask(String url, DownloadFile destFile){
        return new SingleThreadDownloadTask(url, destFile);
    }

    public static SingleThreadDownloadTask newSingleThreadDownloadTask(String url, File destFile){
        return new SingleThreadDownloadTask(url, destFile);
    }

    public static SingleThreadDownloadTask newSingleThreadDownloadTask(String url, String destFilePath){
        return new SingleThreadDownloadTask(url, destFilePath);
    }

    public static MultiThreadDownloadTask newMultiThreadDownloadTask(String url, DownloadFile destFile, int threadCount){
        return new MultiThreadDownloadTask(url, destFile, threadCount);
    }

    public static MultiThreadDownloadTask newMultiThreadDownloadTask(String url, File destFile, int threadCount){
        return new MultiThreadDownloadTask(url, destFile, threadCount);
    }

    public static MultiThreadDownloadTask newMultiThreadDownloadTask(String url, String destFilePath, int threadCount){
        return new MultiThreadDownloadTask(url, destFilePath, threadCount);
    }

    public static void setClientConfigurator(HttpClient.OkHttpClientConfigurator customConfigurator){
        HttpClient.setConfigurator(customConfigurator);
    }
}
