package com.longx.intelligent.lib.longdownloadengine.core.task;

import com.longx.intelligent.lib.longdownloadengine.core.Inspect.InspectResult;
import com.longx.intelligent.lib.longdownloadengine.core.client.HttpClient;
import com.longx.intelligent.lib.longdownloadengine.core.client.Proxy;
import com.longx.intelligent.lib.longdownloadengine.core.downloader.Downloader;
import com.longx.intelligent.lib.longdownloadengine.core.downloader.DownloaderInterface;
import com.longx.intelligent.lib.longdownloadengine.core.yier.CompletedYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ExitYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.StatusChangeYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ProgressIncreaseYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ProgressYier;
import com.longx.intelligent.lib.longdownloadengine.core.io.DownloadFile;
import com.longx.intelligent.lib.longdownloadengine.core.io.JavaDownloadFile;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ErrorYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.RemainingTimeYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.SpeedYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.StartYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ElapsedTimeYier;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import okhttp3.OkHttpClient;

/**
 * Created by LONG on 2026/9/6 at 14:19.
 */
public class SingleThreadDownloadTask implements DownloaderInterface<SingleThreadDownloadTask> {
    private final Downloader downloader;
    private final String url;
    private volatile ExitYier<TaskStates> exitYier;
    private volatile boolean ignoreFileCheck = false;

    public SingleThreadDownloadTask(String url, DownloadFile destFile) {
        this.url = url;
        this.downloader = new Downloader(url, destFile);
    }

    public SingleThreadDownloadTask(String url, File destFile) {
        this(url, new JavaDownloadFile(destFile));
    }

    public SingleThreadDownloadTask(String url, String destFilePath) {
        this(url, new JavaDownloadFile(new File(destFilePath)));
    }

    public SingleThreadDownloadTask configureOkHttpClient(HttpClient.OkHttpClientConfigurator configurator) {
        downloader.configureOkHttpClient(configurator);
        return this;
    }

    public SingleThreadDownloadTask useOkHttpClient(OkHttpClient client) {
        downloader.useOkHttpClient(client);
        return this;
    }

    public SingleThreadDownloadTask setIgnoreFileCheck(boolean ignoreFileCheck) {
        this.ignoreFileCheck = ignoreFileCheck;
        return this;
    }

    public boolean isIgnoreFileCheck() {
        return ignoreFileCheck;
    }

    public static class TaskStates {
        private int status;
        private String url;
        private DownloadFile destFile;
        private Map<String, String> headers;
        private transient Proxy proxy;
        private String tag;
        private long downloaded;
        private int maxRetryCount;
        private boolean followRedirects;

        public TaskStates() {
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public DownloadFile getDestFile() {
            return destFile;
        }

        public void setDestFile(DownloadFile destFile) {
            this.destFile = destFile;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public Proxy getProxy() {
            return proxy;
        }

        public void setProxy(Proxy proxy) {
            this.proxy = proxy;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public long getDownloaded() {
            return downloaded;
        }

        public void setDownloaded(long downloaded) {
            this.downloaded = downloaded;
        }

        public int getMaxRetryCount() {
            return maxRetryCount;
        }

        public void setMaxRetryCount(int maxRetryCount) {
            this.maxRetryCount = maxRetryCount;
        }

        public boolean isFollowRedirects() {
            return followRedirects;
        }

        public void setFollowRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }
    }

    public TaskStates exit() {
        stopDownload();
        TaskStates states = new TaskStates();
        states.setUrl(this.url);
        states.setDestFile(downloader.getDestFile());
        states.setHeaders(downloader.getHeaders());
        states.setProxy(downloader.getProxy());
        states.setTag(downloader.getTag());
        states.setDownloaded(downloader.getDownloaded());
        states.setMaxRetryCount(downloader.getMaxRetryCount());
        states.setFollowRedirects(downloader.isFollowRedirects());
        states.setStatus(downloader.getStatus());
        ExitYier<TaskStates> exitYie = this.exitYier;
        if (exitYie != null) {
            exitYie.onExit(states);
        }
        return states;
    }

    public static SingleThreadDownloadTask buildUnfinishedInstance(TaskStates status) {
        if (status == null || status.getStatus() == STATUS_COMPLETED) {
            return null;
        }
        SingleThreadDownloadTask task = new SingleThreadDownloadTask(status.getUrl(), status.getDestFile());
        if (status.getHeaders() != null) {
            task.setHeaders(status.getHeaders());
        }
        if (status.getProxy() != null) {
            task.setProxy(status.getProxy());
        }
        task.setTag(status.getTag());
        task.setDownloaded(status.getDownloaded());
        task.setMaxRetryCount(status.getMaxRetryCount());
        task.setFollowRedirects(status.isFollowRedirects());
        return task;
    }

    @Override
    public SingleThreadDownloadTask setHeader(String name, String value) {
        downloader.setHeader(name, value);
        return this;
    }

    @Override
    public SingleThreadDownloadTask setHeaders(Map<String, String> headers) {
        downloader.setHeaders(headers);
        return this;
    }

    @Override
    public Map<String, String> getHeaders() {
        return downloader.getHeaders();
    }

    @Override
    public SingleThreadDownloadTask setProxy(Proxy proxy) {
        downloader.setProxy(proxy);
        return this;
    }

    @Override
    public Proxy getProxy() {
        return downloader.getProxy();
    }

    @Override
    public SingleThreadDownloadTask setFollowRedirects(boolean followRedirects) {
        downloader.setFollowRedirects(followRedirects);
        return this;
    }

    @Override
    public boolean isFollowRedirects() {
        return downloader.isFollowRedirects();
    }

    @Override
    public int getStatus() {
        return downloader.getStatus();
    }

    public SingleThreadDownloadTask setErrorYier(ErrorYier errorYier) {
        downloader.setErrorYier(errorYier);
        return this;
    }

    public SingleThreadDownloadTask setProgressYier(long intervalMs, ProgressYier progressYier) {
        downloader.setProgressYier(intervalMs, progressYier);
        return this;
    }

    public SingleThreadDownloadTask setProgressYier(ProgressYier progressYier) {
        setProgressYier(0, progressYier);
        return this;
    }

    public SingleThreadDownloadTask setProgressIncreaseYier(ProgressIncreaseYier progressIncreaseYier) {
        downloader.setProgressIncreaseYier(progressIncreaseYier);
        return this;
    }

    public SingleThreadDownloadTask setProgressIncreaseYier(long intervalMs, ProgressIncreaseYier progressIncreaseYier) {
        downloader.setProgressIncreaseYier(intervalMs, progressIncreaseYier);
        return this;
    }

    public SingleThreadDownloadTask setCompletedYier(CompletedYier completedYier) {
        downloader.setCompletedYier(completedYier);
        return this;
    }

    public SingleThreadDownloadTask setStartYier(StartYier startYier) {
        downloader.setStartYier(startYier);
        return this;
    }

    @Override
    public InspectResult inspect() throws IOException {
        return downloader.inspect();
    }

    @Override
    public SingleThreadDownloadTask setAutoResolveName(boolean autoResolveName) {
        downloader.setAutoResolveName(autoResolveName);
        return this;
    }

    @Override
    public boolean isAutoResolveName() {
        return downloader.isAutoResolveName();
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public DownloadFile getDestFile() {
        return downloader.getDestFile();
    }

    @Override
    public long getLength() {
        return downloader.getLength();
    }

    @Override
    public SingleThreadDownloadTask setDownloaded(long downloaded) {
        downloader.setDownloaded(downloaded);
        return this;
    }

    @Override
    public boolean startDownload() {
        long downloaded = downloader.getDownloaded();
        if (downloaded > 0) {
            DownloadFile destFile = downloader.getDestFile();
            boolean fileValid = destFile != null && destFile.exists() && destFile.length() >= downloaded && destFile.length() <= getLength();
            if (!fileValid) {
                if (!ignoreFileCheck) {
                    new Thread(() -> {
                        Exception e = new IOException("断点续传失败，本地文件不存在或损坏");
                        ErrorYier yier = downloader.getErrorYier();
                        if (yier != null) {
                            yier.onError(getTag(), e);
                        }
                    }, "SingleThreadTask-CheckFailure").start();
                    return false;
                } else {
                    downloader.setDownloaded(0);
                }
            }
        }
        return downloader.startDownload();
    }

    @Override
    public boolean startDownloadSync() {
        long downloaded = downloader.getDownloaded();
        if (downloaded > 0) {
            DownloadFile destFile = downloader.getDestFile();
            boolean fileValid = destFile != null && destFile.exists() && destFile.length() >= downloaded && destFile.length() <= getLength();
            if (!fileValid) {
                if (!ignoreFileCheck) {
                    Exception e = new IOException("同步下载断点续传失败，本地文件不存在或损坏");
                    ErrorYier yier = downloader.getErrorYier();
                    if (yier != null) {
                        yier.onError(getTag(), e);
                    }
                    return false;
                } else {
                    downloader.setDownloaded(0);
                }
            }
        }
        return downloader.startDownloadSync();
    }

    @Override
    public boolean pauseDownload() {
        return downloader.pauseDownload();
    }

    @Override
    public boolean resumeDownload() {
        return downloader.resumeDownload();
    }

    @Override
    public void stopDownload() {
        downloader.stopDownload();
    }

    @Override
    public long getDownloaded() {
        return downloader.getDownloaded();
    }

    @Override
    public boolean hasStarted() {
        return downloader.hasStarted();
    }

    @Override
    public boolean isInDownloading() {
        return downloader.isInDownloading();
    }

    @Override
    public boolean isStopped() {
        return downloader.isStopped();
    }

    @Override
    public boolean isPaused() {
        return downloader.isPaused();
    }

    @Override
    public boolean isCompleted() {
        return downloader.isCompleted();
    }

    @Override
    public SingleThreadDownloadTask setTag(String tag) {
        downloader.setTag(tag);
        return this;
    }

    @Override
    public String getTag() {
        return downloader.getTag();
    }

    @Override
    public SingleThreadDownloadTask setMaxRetryCount(int maxRetryCount) {
        downloader.setMaxRetryCount(maxRetryCount);
        return this;
    }

    @Override
    public int getMaxRetryCount() {
        return downloader.getMaxRetryCount();
    }

    public SingleThreadDownloadTask setSpeedYier(long intervalMs, SpeedYier speedYier) {
        downloader.setSpeedYier(intervalMs, speedYier);
        return this;
    }

    public long getSpeed() {
        return downloader.getSpeed();
    }

    public SingleThreadDownloadTask setRemainingTimeYier(long intervalMs, RemainingTimeYier remainingTimeYier) {
        downloader.setRemainingTimeYier(intervalMs, remainingTimeYier);
        return this;
    }

    public long getRemainingTime() {
        return downloader.getRemainingTime();
    }

    public SingleThreadDownloadTask setElapsedTimeYier(long intervalMs, ElapsedTimeYier elapsedTimeYier) {
        downloader.setElapsedTimeYier(intervalMs, elapsedTimeYier);
        return this;
    }

    public long getElapsedTime() {
        return downloader.getElapsedTime();
    }

    public SingleThreadDownloadTask setStatusChangeYier(StatusChangeYier statusChangeYier) {
        downloader.setStatusChangeYier(statusChangeYier);
        return this;
    }

    @Override
    public SingleThreadDownloadTask setMaxSpeed(long maxBytesPerMs) {
        downloader.setMaxSpeed(maxBytesPerMs);
        return this;
    }

    public SingleThreadDownloadTask setExitYier(ExitYier<TaskStates> exitYier) {
        this.exitYier = exitYier;
        return this;
    }
}