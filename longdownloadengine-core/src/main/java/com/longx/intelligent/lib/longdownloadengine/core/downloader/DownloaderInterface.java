package com.longx.intelligent.lib.longdownloadengine.core.downloader;

import com.longx.intelligent.lib.longdownloadengine.core.Inspect.InspectResult;
import com.longx.intelligent.lib.longdownloadengine.core.client.Proxy;
import com.longx.intelligent.lib.longdownloadengine.core.io.DownloadFile;

import java.io.IOException;
import java.util.Map;

/**
 * Created by LONG on 2026/9/5 at 23:14.
 */
public interface DownloaderInterface<T extends DownloaderInterface<T>> {
    int STATUS_IDLE = 0;
    int STATUS_STARTED = 1;
    int STATUS_DOWNLOADING = 2;
    int STATUS_STOPPED = 3;
    int STATUS_COMPLETED = 4;
    int STATUS_FAILED = 5;
    int STATUS_PAUSED = 6;

    InspectResult inspect() throws IOException;
    String getUrl();
    DownloadFile getDestFile();
    long getLength();
    T setHeader(String name, String value);
    T setHeaders(Map<String, String> headers);
    Map<String, String> getHeaders();
    T setProxy(Proxy proxy);
    Proxy getProxy();
    T setFollowRedirects(boolean followRedirects);
    boolean isFollowRedirects();
    int getStatus();
    T setDownloaded(long downloaded);
    boolean startDownload();
    boolean startDownloadSync();
    boolean pauseDownload();
    boolean resumeDownload();
    void stopDownload();
    long getDownloaded();
    boolean hasStarted();
    boolean isInDownloading();
    boolean isStopped();
    boolean isPaused();
    boolean isCompleted();
    T setTag(String tag);
    String getTag();
    T setMaxRetryCount(int maxRetryCount);
    int getMaxRetryCount();
    T setMaxSpeed(long maxBytesPerSecond);
    T setAutoResolveName(boolean autoResolveName);
    boolean isAutoResolveName();
}