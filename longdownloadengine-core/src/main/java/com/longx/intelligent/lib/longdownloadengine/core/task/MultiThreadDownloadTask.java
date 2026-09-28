package com.longx.intelligent.lib.longdownloadengine.core.task;

import com.longx.intelligent.lib.longdownloadengine.core.Inspect.DownloadInspector;
import com.longx.intelligent.lib.longdownloadengine.core.Inspect.InspectResult;
import com.longx.intelligent.lib.longdownloadengine.core.client.HttpClient;
import com.longx.intelligent.lib.longdownloadengine.core.client.Proxy;
import com.longx.intelligent.lib.longdownloadengine.core.downloader.DownloaderInterface;
import com.longx.intelligent.lib.longdownloadengine.core.downloader.PartDownloader;
import com.longx.intelligent.lib.longdownloadengine.core.io.SpeedLimiter;
import com.longx.intelligent.lib.longdownloadengine.core.value.Value;
import com.longx.intelligent.lib.longdownloadengine.core.yier.CompletedYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ExitYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.PartProgressYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ProgressIncreaseYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ProgressYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.RemainingTimeYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.SpeedYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.StatusChangeYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ElapsedTimeYier;
import com.longx.intelligent.lib.longdownloadengine.core.io.DownloadFile;
import com.longx.intelligent.lib.longdownloadengine.core.io.JavaDownloadFile;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ErrorYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.StartYier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;

/**
 * Created by LONG on 2026/9/6 at 13:38.
 */
public class MultiThreadDownloadTask implements DownloaderInterface<MultiThreadDownloadTask> {
    private final String url;
    private volatile DownloadFile destFile;
    private volatile boolean autoResolveName = true;
    private final int threadCount;
    private volatile String tag;
    private final AtomicInteger status = new AtomicInteger(STATUS_IDLE);
    private volatile long totalLength = -1;
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    private volatile int maxRetryCount = Value.DEFAULT_DOWNLOADER_MAX_RETRY_COUNT;
    private final Map<String, String> headers = new ConcurrentHashMap<>();
    private volatile Proxy proxy;
    private volatile boolean followRedirects = true;
    private volatile boolean fallbackToSingleThread = true;
    private volatile boolean ignoreFileCheck = false;
    private volatile ProgressYier progressYier;
    private volatile long progressIntervalMs = 0;
    private final AtomicLong lastProgressCallbackTime = new AtomicLong(0);
    private volatile ProgressIncreaseYier progressIncreaseYier;
    private volatile PartProgressYier partProgressYier;
    private volatile long segmentProgressIntervalMs = 1000;
    private volatile ErrorYier errorYier;
    private volatile StartYier startYier;
    private volatile CompletedYier completedYier;
    private volatile StatusChangeYier statusChangeYier;
    private volatile StatusChangeYier segmentStatusChangeYier;
    private volatile SpeedYier speedYier;
    private volatile long speedIntervalMs = 1000;
    private volatile long currentSpeed = 0;
    private final AtomicLong bytesSinceLastSpeedCalculation = new AtomicLong(0);
    private volatile RemainingTimeYier remainingTimeYier;
    private volatile long remainingTimeIntervalMs = 1000;
    private volatile long currentRemainingTime = -1;
    private volatile long averageSpeedWindowMs = 5000;
    private final Deque<long[]> downloadProgressHistory = new ConcurrentLinkedDeque<>();
    private volatile ElapsedTimeYier elapsedTimeYier;
    private volatile long elapsedTimeIntervalMs = 1000;
    private volatile long currentElapsedTime = 0;
    private volatile ScheduledExecutorService timerScheduler;
    private final List<DownloadSegment> activeSegments = new CopyOnWriteArrayList<>();
    private volatile List<SegmentStates> initialSegmentStatusses;
    private volatile CountDownLatch syncLatch;
    private volatile boolean useSegmentTags = true;
    private volatile SpeedLimiter speedLimiter;
    private volatile ExitYier<TaskStates> exitYier;
    private volatile boolean callbackRemainingTimeBeforeWindowFilled = true;
    private volatile Exception failedException;
    private volatile OkHttpClient customOkHttpClient;
    private volatile HttpClient.OkHttpClientConfigurator okHttpClientConfigurator;
    private volatile long progressIncreaseIntervalMs = 0;
    private final AtomicLong lastProgressIncreaseCallbackTime = new AtomicLong(0);
    private final AtomicLong accumulatedProgressIncrease = new AtomicLong(0);

    public MultiThreadDownloadTask(String url, DownloadFile destFile, int threadCount) {
        this.url = url;
        this.destFile = destFile;
        this.threadCount = Math.max(1, threadCount);
    }

    public MultiThreadDownloadTask(String url, File destFile, int threadCount) {
        this(url, new JavaDownloadFile(destFile), threadCount);
    }

    public MultiThreadDownloadTask(String url, String destFilePath, int threadCount) {
        this(url, new JavaDownloadFile(new File(destFilePath)), threadCount);
    }

    public MultiThreadDownloadTask configureOkHttpClient(HttpClient.OkHttpClientConfigurator configurator) {
        this.okHttpClientConfigurator = configurator;
        return this;
    }

    public MultiThreadDownloadTask useOkHttpClient(OkHttpClient client) {
        this.customOkHttpClient = client;
        return this;
    }

    private boolean compareAndSetStatus(int expect, int update) {
        return compareAndSetStatus(expect, update, null);
    }

    private boolean compareAndSetStatus(int expect, int update, Exception e) {
        boolean success = status.compareAndSet(expect, update);
        if (success) {
            if (update == STATUS_FAILED && e != null) {
                this.failedException = e;
            }
            notifyStatusChanged(expect, update, e);
        }
        return success;
    }

    private void notifyStatusChanged(int oldStatus, int newStatus, Exception e) {
        if (oldStatus == newStatus) return;
        StatusChangeYier yier = this.statusChangeYier;
        if (yier != null) {
            yier.onStatusChanged(tag, oldStatus, newStatus, e);
        }
    }

    public MultiThreadDownloadTask setIgnoreFileCheck(boolean ignoreFileCheck) {
        this.ignoreFileCheck = ignoreFileCheck;
        return this;
    }

    public boolean isIgnoreFileCheck() {
        return ignoreFileCheck;
    }

    public MultiThreadDownloadTask setUseSegmentTags(boolean useSegmentTags) {
        this.useSegmentTags = useSegmentTags;
        return this;
    }

    public boolean isUseSegmentTags() {
        return useSegmentTags;
    }

    public MultiThreadDownloadTask setStatusChangeYier(StatusChangeYier statusChangeYier) {
        this.statusChangeYier = statusChangeYier;
        if (this.statusChangeYier != null) {
            this.statusChangeYier.onStatusChanged(tag, -1, status.get(), this.failedException);
        }
        return this;
    }

    public MultiThreadDownloadTask setSegmentStatusChangeYier(StatusChangeYier segmentStatusChangeYier) {
        this.segmentStatusChangeYier = segmentStatusChangeYier;
        return this;
    }

    public MultiThreadDownloadTask setSpeedYier(long intervalMs, SpeedYier speedYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("速度计算间隔必须大于等于 0 毫秒");
        this.speedIntervalMs = intervalMs;
        this.speedYier = speedYier;
        return this;
    }

    public long getSpeed() {
        return currentSpeed;
    }

    @Override
    public MultiThreadDownloadTask setMaxSpeed(long maxBytesPerMs) {
        if (this.speedLimiter == null) {
            this.speedLimiter = new SpeedLimiter(maxBytesPerMs);
        } else {
            this.speedLimiter.setMaxBytesPerMs(maxBytesPerMs);
        }
        return this;
    }

    public MultiThreadDownloadTask setRemainingTimeYier(long intervalMs, boolean callbackBeforeWindowFilled, RemainingTimeYier remainingTimeYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("剩余时间计算间隔必须大于等于 0 毫秒");
        this.remainingTimeIntervalMs = intervalMs;
        this.callbackRemainingTimeBeforeWindowFilled = callbackBeforeWindowFilled;
        this.remainingTimeYier = remainingTimeYier;
        return this;
    }

    public MultiThreadDownloadTask setRemainingTimeYier(long intervalMs, RemainingTimeYier remainingTimeYier) {
        return setRemainingTimeYier(intervalMs, true, remainingTimeYier);
    }

    public MultiThreadDownloadTask setAverageSpeedWindowMs(long averageSpeedWindowMs) {
        if (averageSpeedWindowMs <= 0) throw new IllegalArgumentException("平均速度窗口必须大于 0 毫秒");
        this.averageSpeedWindowMs = averageSpeedWindowMs;
        return this;
    }

    public long getRemainingTime() {
        return currentRemainingTime;
    }

    public MultiThreadDownloadTask setElapsedTimeYier(long intervalMs, ElapsedTimeYier elapsedTimeYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("已用时间计算间隔必须大于等于 0 毫秒");
        this.elapsedTimeIntervalMs = intervalMs;
        this.elapsedTimeYier = elapsedTimeYier;
        return this;
    }

    public long getElapsedTime() {
        return currentElapsedTime;
    }

    private synchronized void startTimers() {
        stopTimers();
        timerScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MultiThreadTask-Timer");
            thread.setDaemon(true);
            return thread;
        });
        if (elapsedTimeYier != null) {
            elapsedTimeYier.timeElapsed(tag, currentElapsedTime);
            final long sessionStartTime = System.currentTimeMillis();
            final long baseElapsedTime = currentElapsedTime;
            timerScheduler.scheduleWithFixedDelay(() -> {
                int stat = status.get();
                if (stat == STATUS_STARTED || stat == STATUS_DOWNLOADING) {
                    currentElapsedTime = baseElapsedTime + (System.currentTimeMillis() - sessionStartTime);
                    ElapsedTimeYier elapsedTimeYie = elapsedTimeYier;
                    if (elapsedTimeYie != null) elapsedTimeYie.timeElapsed(tag, currentElapsedTime);
                }
            }, elapsedTimeIntervalMs, elapsedTimeIntervalMs, TimeUnit.MILLISECONDS);
        }
        if (speedYier != null) {
            timerScheduler.scheduleWithFixedDelay(new Runnable() {
                long lastTick = System.currentTimeMillis();

                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    long timeDiff = now - lastTick;
                    if (status.get() == STATUS_DOWNLOADING) {
                        if (timeDiff > 0) {
                            long bytes = bytesSinceLastSpeedCalculation.getAndSet(0);
                            currentSpeed = (long) (bytes / (double) timeDiff);
                            SpeedYier speedYie = speedYier;
                            if (speedYie != null) speedYie.onSpeed(tag, currentSpeed);
                        }
                    } else {
                        bytesSinceLastSpeedCalculation.set(0);
                    }
                    lastTick = now;
                }
            }, speedIntervalMs, speedIntervalMs, TimeUnit.MILLISECONDS);
        }
        if (remainingTimeYier != null) {
            downloadProgressHistory.clear();
            final long remainingTimeSessionStartTime = System.currentTimeMillis();
            timerScheduler.scheduleWithFixedDelay(() -> {
                if (status.get() == STATUS_DOWNLOADING) {
                    RemainingTimeYier remainingTimeYie = remainingTimeYier;
                    if (remainingTimeYie != null) {
                        long now = System.currentTimeMillis();
                        long currentTotalLength = getLength();
                        long currentDownloadedBytes = getDownloaded();
                        downloadProgressHistory.addLast(new long[]{now, currentDownloadedBytes});
                        while (downloadProgressHistory.size() > 1 && (now - downloadProgressHistory.peekFirst()[0]) > averageSpeedWindowMs) {
                            downloadProgressHistory.pollFirst();
                        }
                        if (currentTotalLength > 0) {
                            long remainingBytes = currentTotalLength - currentDownloadedBytes;
                            long[] oldestRecord = downloadProgressHistory.peekFirst();
                            if (oldestRecord != null && downloadProgressHistory.size() > 1) {
                                long timeDiff = now - oldestRecord[0];
                                long bytesDiff = currentDownloadedBytes - oldestRecord[1];
                                if (timeDiff > 0 && bytesDiff > 0) {
                                    double averageSpeed = (double) bytesDiff / timeDiff;
                                    currentRemainingTime = Math.max(0, (long) (remainingBytes / averageSpeed));
                                } else if (timeDiff > 0 && bytesDiff == 0) {
                                    currentRemainingTime = -1;
                                } else if (currentSpeed > 0) {
                                    currentRemainingTime = Math.max(0, (long) (remainingBytes / (double) currentSpeed));
                                } else {
                                    currentRemainingTime = -1;
                                }
                            } else if (currentSpeed > 0) {
                                currentRemainingTime = Math.max(0, (long) (remainingBytes / (double) currentSpeed));
                            } else {
                                currentRemainingTime = -1;
                            }
                        } else {
                            currentRemainingTime = -1;
                        }
                        if (callbackRemainingTimeBeforeWindowFilled || (now - remainingTimeSessionStartTime) >= averageSpeedWindowMs) {
                            remainingTimeYie.timeRemaining(tag, currentRemainingTime);
                        }
                    }
                }
            }, remainingTimeIntervalMs, remainingTimeIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void stopTimers() {
        if (timerScheduler != null) {
            timerScheduler.shutdownNow();
            timerScheduler = null;
        }
    }

    private void resetSpeed() {
        currentSpeed = 0;
        currentRemainingTime = -1;
        bytesSinceLastSpeedCalculation.set(0);
        downloadProgressHistory.clear();
        lastProgressCallbackTime.set(0);
        SpeedYier speedYie = speedYier;
        if (speedYie != null) speedYie.onSpeed(tag, 0);
        RemainingTimeYier remainingTimeYie = remainingTimeYier;
        if (remainingTimeYie != null) remainingTimeYie.timeRemaining(tag, -1);
        ElapsedTimeYier elapsedTimeYie = elapsedTimeYier;
        if (elapsedTimeYie != null) elapsedTimeYie.timeElapsed(tag, currentElapsedTime);
    }

    public static class SegmentStates {
        private int status;
        private String tag;
        private long startOffset;
        private long endOffset;
        private long stopBoundary;
        private long downloaded;

        public SegmentStates() {
        }

        public long getStartOffset() {
            return startOffset;
        }

        public void setStartOffset(long startOffset) {
            this.startOffset = startOffset;
        }

        public long getEndOffset() {
            return endOffset;
        }

        public void setEndOffset(long endOffset) {
            this.endOffset = endOffset;
        }

        public long getStopBoundary() {
            return stopBoundary;
        }

        public void setStopBoundary(long stopBoundary) {
            this.stopBoundary = stopBoundary;
        }

        public long getDownloaded() {
            return downloaded;
        }

        public void setDownloaded(long downloaded) {
            this.downloaded = downloaded;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }
    }

    public static class TaskStates {
        private int status;
        private String url;
        private DownloadFile destFile;
        private int threadCount;
        private Map<String, String> headers;
        private Proxy proxy;
        private String tag;
        private long totalDownloaded;
        private long totalLength;
        private int maxRetryCount;
        private boolean followRedirects;
        private boolean fallbackToSingleThread;
        private List<SegmentStates> segmentStatusses;
        private boolean useSegmentTags = true;
        private Exception failedException;

        public TaskStates() {
        }

        public int getStatus() {
            return status;
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

        public int getThreadCount() {
            return threadCount;
        }

        public void setThreadCount(int threadCount) {
            this.threadCount = threadCount;
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

        public long getTotalDownloaded() {
            return totalDownloaded;
        }

        public void setTotalDownloaded(long totalDownloaded) {
            this.totalDownloaded = totalDownloaded;
        }

        public long getTotalLength() {
            return totalLength;
        }

        public void setTotalLength(long totalLength) {
            this.totalLength = totalLength;
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

        public boolean isFallbackToSingleThread() {
            return fallbackToSingleThread;
        }

        public void setFallbackToSingleThread(boolean fallbackToSingleThread) {
            this.fallbackToSingleThread = fallbackToSingleThread;
        }

        public List<SegmentStates> getSegmentStatuss() {
            return segmentStatusses;
        }

        public void setSegmentStatuss(List<SegmentStates> segmentStatusses) {
            this.segmentStatusses = segmentStatusses;
        }

        public boolean isUseSegmentTags() {
            return useSegmentTags;
        }

        public void setUseSegmentTags(boolean useSegmentTags) {
            this.useSegmentTags = useSegmentTags;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public Exception getFailedException() {
            return failedException;
        }

        public void setFailedException(Exception failedException) {
            this.failedException = failedException;
        }
    }

    public TaskStates exit() {
        stopDownload();
        TaskStates states = new TaskStates();
        states.setStatus(status.get());
        states.setUrl(this.url);
        states.setDestFile(this.destFile);
        states.setThreadCount(this.threadCount);
        states.setHeaders(this.headers);
        states.setProxy(this.proxy);
        states.setTag(this.tag);
        states.setTotalDownloaded(this.totalDownloaded.get());
        states.setTotalLength(this.totalLength);
        states.setMaxRetryCount(this.maxRetryCount);
        states.setFollowRedirects(this.followRedirects);
        states.setFallbackToSingleThread(this.fallbackToSingleThread);
        states.setUseSegmentTags(this.useSegmentTags);
        states.setFailedException(this.failedException);
        List<SegmentStates> segmentStatusses = new ArrayList<>();
        if (!activeSegments.isEmpty()) {
            for (DownloadSegment segment : activeSegments) {
                SegmentStates ss = new SegmentStates();
                ss.setStartOffset(segment.startOffset);
                ss.setEndOffset(segment.endOffset);
                ss.setStopBoundary(segment.stopBoundary);
                ss.setDownloaded(segment.downloader.getDownloaded());
                ss.setStatus(segment.downloader.getStatus());
                ss.setTag(segment.downloader.getTag());
                segmentStatusses.add(ss);
            }
        } else if (initialSegmentStatusses != null) {
            segmentStatusses.addAll(initialSegmentStatusses);
        }
        states.setSegmentStatuss(segmentStatusses);
        ExitYier<TaskStates> exitYie = this.exitYier;
        if (exitYie != null) {
            exitYie.onExit(states);
        }
        return states;
    }

    public static MultiThreadDownloadTask buildUnfinishedInstance(TaskStates status) {
        if (status == null || status.getStatus() == STATUS_COMPLETED) return null;
        MultiThreadDownloadTask task = new MultiThreadDownloadTask(status.getUrl(), status.getDestFile(), status.getThreadCount());
        task.status.set(status.getStatus());
        if (status.getHeaders() != null) task.setHeaders(status.getHeaders());
        if (status.getProxy() != null) task.setProxy(status.getProxy());
        task.setTag(status.getTag());
        task.setDownloaded(status.getTotalDownloaded());
        task.totalLength = status.getTotalLength();
        task.setMaxRetryCount(status.getMaxRetryCount());
        task.setFollowRedirects(status.isFollowRedirects());
        task.setFallbackToSingleThread(status.isFallbackToSingleThread());
        task.setUseSegmentTags(status.isUseSegmentTags());
        task.failedException = status.getFailedException();
        task.initialSegmentStatusses = status.getSegmentStatuss();
        return task;
    }

    protected void countDownSyncLatch() {
        CountDownLatch latch = this.syncLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    protected void notifyError(Exception e) {
        resetSpeed();
        stopTimers();
        ErrorYier yier = this.errorYier;
        if (yier != null) yier.onError(tag, e);
        countDownSyncLatch();
    }

    protected void notifyStart() {
        bytesSinceLastSpeedCalculation.set(0);
        currentSpeed = 0;
        currentRemainingTime = -1;
        StartYier yier = this.startYier;
        if (yier != null) yier.onStart(tag, totalLength);
    }

    protected void notifyCompleted() {
        if (progressIncreaseYier != null && progressIncreaseIntervalMs > 0) {
            long remainingBytes = accumulatedProgressIncrease.getAndSet(0);
            if (remainingBytes > 0) {
                progressIncreaseYier.onProgressIncrease(tag, remainingBytes);
            }
        }
        resetSpeed();
        stopTimers();
        CompletedYier yier = this.completedYier;
        if (yier != null) yier.onCompleted(tag);
        countDownSyncLatch();
    }

    @Override
    public boolean startDownloadSync() {
        syncLatch = new CountDownLatch(1);
        if (!startDownload()) {
            syncLatch = null;
            return false;
        }
        try {
            syncLatch.await();
        } catch (InterruptedException e) {
            stopDownload();
            Thread.currentThread().interrupt();
            return false;
        } finally {
            syncLatch = null;
        }
        return isCompleted();
    }

    @Override
    public boolean startDownload() {
        while (true) {
            int s = status.get();
            if (s == STATUS_STARTED || s == STATUS_DOWNLOADING) return false;
            if (compareAndSetStatus(s, STATUS_STARTED)) {
                if (s == STATUS_FAILED) {
                    currentElapsedTime = 0;
                }
                startTimers();
                new Thread(() -> {
                    try {
                        List<SegmentStates> initSegmentStatusses = initialSegmentStatusses;
                        if (initSegmentStatusses != null && !initSegmentStatusses.isEmpty()) {
                            long currentDownloaded = totalDownloaded.get();
                            if (currentDownloaded > 0) {
                                boolean fileValid = destFile != null && destFile.exists() && destFile.length() >= currentDownloaded && destFile.length() <= getLength();
                                if (!fileValid) {
                                    if (!ignoreFileCheck) {
                                        throw new IOException("断点续传失败，本地文件不存在或损坏");
                                    } else {
                                        totalDownloaded.set(0);
                                        initialSegmentStatusses = null;
                                        initSegmentStatusses = null;
                                    }
                                }
                            }
                            if (initSegmentStatusses != null) {
                                ensureFileExists();
                                activeSegments.clear();
                                notifyStart();
                                for (SegmentStates segmentStates : initSegmentStatusses) {
                                    DownloadSegment segment = new DownloadSegment(segmentStates.getStartOffset(), segmentStates.getEndOffset());
                                    segment.stopBoundary = segmentStates.getStopBoundary();
                                    segment.downloader.setDownloaded(segmentStates.getDownloaded());
                                    segment.reportedBytes.set(segmentStates.getDownloaded());
                                    activeSegments.add(segment);
                                    segment.downloader.startDownload();
                                }
                                initialSegmentStatusses = null;
                            }
                        }
                        if (initSegmentStatusses == null) {
                            InspectResult inspectResult = inspect();
                            if (inspectResult == null) throw new IOException("无法获取网络状态");
                            if (inspectResult.getResolvedDestFile() != null) {
                                destFile = inspectResult.getResolvedDestFile();
                            }
                            ensureFileExists();
                            totalLength = inspectResult.getLength();
                            notifyStart();
                            if (totalLength <= 0) {
                                if (fallbackToSingleThread) {
                                    totalLength = -1;
                                    activeSegments.clear();
                                    DownloadSegment segment = new DownloadSegment(0, -1);
                                    activeSegments.add(segment);
                                    segment.downloader.startDownload();
                                } else {
                                    throw new IOException("无法获取网络文件大小，且未开启单线程降级");
                                }
                            } else {
                                int actualThreadCount = inspectResult.isSupportRange() ? threadCount : (fallbackToSingleThread ? 1 : 0);
                                if (actualThreadCount == 0)
                                    throw new IOException("服务器不支持范围下载，且未开启单线程降级");
                                activeSegments.clear();
                                long partSize = totalLength / actualThreadCount;
                                for (int i = 0; i < actualThreadCount; i++) {
                                    long start = i * partSize;
                                    long end = (i == actualThreadCount - 1) ? totalLength - 1 : (start + partSize - 1);
                                    DownloadSegment segment = new DownloadSegment(start, end);
                                    activeSegments.add(segment);
                                    segment.downloader.startDownload();
                                }
                            }
                        }
                        if (!compareAndSetStatus(STATUS_STARTED, STATUS_DOWNLOADING)) {
                            int currentStatus = status.get();
                            if (currentStatus == STATUS_PAUSED) {
                                resetSpeed();
                                stopTimers();
                                for (DownloadSegment downloadSegment : activeSegments)
                                    downloadSegment.downloader.pauseDownload();
                            } else if (currentStatus == STATUS_STOPPED) {
                                resetSpeed();
                                stopTimers();
                                stopAllWorkers();
                            }
                        }
                    } catch (Exception e) {
                        resetSpeed();
                        stopTimers();
                        if (compareAndSetStatus(STATUS_STARTED, STATUS_FAILED, e) || compareAndSetStatus(STATUS_DOWNLOADING, STATUS_FAILED, e)) {
                            stopAllWorkers();
                            notifyError(e);
                        }
                    }
                }, "MultiThreadDownload-Setup").start();
                return true;
            }
        }
    }

    private void ensureFileExists() throws IOException {
        if (destFile != null) {
            destFile.createDirs();
            destFile.createNewFile();
        }
    }

    private synchronized void handleWorkerFinished(DownloadSegment finishedSegment) {
        int stat = status.get();
        if (stat == STATUS_PAUSED || stat == STATUS_STOPPED || stat == STATUS_COMPLETED || stat == STATUS_FAILED)
            return;
        activeSegments.remove(finishedSegment);
        boolean shareWorkSuccess = tryShareWork();
        if (!shareWorkSuccess && activeSegments.isEmpty()) {
            if (compareAndSetStatus(stat, STATUS_COMPLETED)) {
                notifyCompleted();
            }
        }
    }

    private boolean tryShareWork() {
        if (activeSegments.isEmpty()) return false;
        DownloadSegment maxSegment = null;
        long maxRemaining = 0;
        for (DownloadSegment downloadSegment : activeSegments) {
            long rem = downloadSegment.getRemaining();
            if (rem > maxRemaining) {
                maxRemaining = rem;
                maxSegment = downloadSegment;
            }
        }
        if (maxRemaining > 256 * 1024) {
            long shareStart = maxSegment.getCurrentPos() + maxRemaining / 2;
            long shareEnd = maxSegment.stopBoundary;
            maxSegment.stopBoundary = shareStart;
            DownloadSegment sharer = new DownloadSegment(shareStart, shareEnd);
            activeSegments.add(sharer);
            sharer.downloader.startDownload();
            return true;
        }
        return false;
    }

    private void stopAllWorkers() {
        for (DownloadSegment downloadSegment : activeSegments) {
            downloadSegment.downloader.stopDownload();
        }
    }

    private class DownloadSegment {
        final PartDownloader downloader;
        final long startOffset;
        final long endOffset;
        volatile long stopBoundary;
        private volatile boolean isFinished = false;
        final AtomicLong reportedBytes = new AtomicLong(0);

        DownloadSegment(long startOffset, long endOffset) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.stopBoundary = endOffset;
            this.downloader = new PartDownloader(url, destFile, startOffset, endOffset);
            this.downloader.setSpeedLimiter(MultiThreadDownloadTask.this.speedLimiter);
            if (customOkHttpClient != null) {
                this.downloader.useOkHttpClient(customOkHttpClient);
            }
            if (okHttpClientConfigurator != null) {
                this.downloader.configureOkHttpClient(okHttpClientConfigurator);
            }
            if (useSegmentTags) {
                String baseTag = (tag == null || tag.isEmpty()) ? "" : tag;
                String endStr = (endOffset == -1) ? "MAX" : String.valueOf(endOffset);
                this.downloader.setTag(baseTag + "[" + startOffset + "-" + endStr + "]");
            } else {
                this.downloader.setTag(tag);
            }
            this.downloader.setHeaders(headers);
            this.downloader.setProxy(proxy);
            this.downloader.setFollowRedirects(followRedirects);
            this.downloader.setMaxRetryCount(maxRetryCount);
            this.downloader.setStatusChangeYier((taskTag, oldStatus, newStatus, e) -> {
                StatusChangeYier segmentYier = segmentStatusChangeYier;
                if (segmentYier != null) {
                    segmentYier.onStatusChanged(taskTag, oldStatus, newStatus, e);
                }
            });
            this.downloader.setCompletedYier(taskTag -> {
                if (!isFinished) {
                    isFinished = true;
                    handleWorkerFinished(this);
                }
            });
            this.downloader.setPartProgressYier(segmentProgressIntervalMs, (taskTag, length, currentDownloaded, progre, startOff, endOff) -> {
                long currentPos = startOff + currentDownloaded;
                if (stopBoundary != -1 && currentPos >= stopBoundary) {
                    this.downloader.stopDownload();
                }
                long maxAllowed = getMaxAllowedDownloaded();
                long validCurrentDownloaded = Math.min(currentDownloaded, maxAllowed);
                if (!isFinished && stopBoundary != -1 && (validCurrentDownloaded >= maxAllowed || currentPos >= stopBoundary)) {
                    isFinished = true;
                    handleWorkerFinished(this);
                }
                PartProgressYier partProgressYie = partProgressYier;
                if (partProgressYie != null) {
                    double progress = 0;
                    long effectiveLength = (stopBoundary != -1) ? maxAllowed : length;
                    if (effectiveLength > 0) {
                        progress = Math.min(1.0, (double) validCurrentDownloaded / effectiveLength);
                    }
                    partProgressYie.onPartProgress(taskTag, effectiveLength, validCurrentDownloaded, progress, startOff, stopBoundary != -1 ? stopBoundary - 1 : endOff);
                }
            });
            this.downloader.setProgressIncreaseYier((taskTag, len) -> {
                long maxAllowed = getMaxAllowedDownloaded();
                long currentlyReported = reportedBytes.get();
                long validLen = 0;
                if (currentlyReported + len <= maxAllowed) {
                    validLen = len;
                } else if (currentlyReported < maxAllowed) {
                    validLen = maxAllowed - currentlyReported;
                }
                if (validLen > 0) {
                    reportedBytes.addAndGet(validLen);
                    long currentTotal = totalDownloaded.addAndGet(validLen);
                    if (speedYier != null || remainingTimeYier != null) {
                        bytesSinceLastSpeedCalculation.addAndGet(validLen);
                    }
                    if (progressIncreaseYier != null) {
                        if (progressIncreaseIntervalMs <= 0) {
                            progressIncreaseYier.onProgressIncrease(taskTag, validLen);
                        } else {
                            accumulatedProgressIncrease.addAndGet(validLen);
                            long now = System.currentTimeMillis();
                            long displayTotal = (totalLength > 0) ? Math.min(currentTotal, totalLength) : currentTotal;
                            boolean isComplete = (totalLength > 0 && displayTotal >= totalLength);
                            long lastTime = lastProgressIncreaseCallbackTime.get();
                            if (isComplete || (now - lastTime >= progressIncreaseIntervalMs)) {
                                if (isComplete || lastProgressIncreaseCallbackTime.compareAndSet(lastTime, now)) {
                                    long bytesToCallback = accumulatedProgressIncrease.getAndSet(0);
                                    if (bytesToCallback > 0) {
                                        progressIncreaseYier.onProgressIncrease(taskTag, bytesToCallback);
                                    }
                                }
                            }
                        }
                    }
                    if (progressYier != null) {
                        long now = System.currentTimeMillis();
                        long displayTotal = (totalLength > 0) ? Math.min(currentTotal, totalLength) : currentTotal;
                        boolean isComplete = (totalLength > 0 && displayTotal >= totalLength);
                        long lastTime = lastProgressCallbackTime.get();
                        if (isComplete || (now - lastTime >= progressIntervalMs)) {
                            if (isComplete || lastProgressCallbackTime.compareAndSet(lastTime, now)) {
                                double progress = 0;
                                if (totalLength > 0) {
                                    progress = (double) displayTotal / totalLength;
                                }
                                progressYier.onProgress(taskTag, totalLength, displayTotal, progress);
                            }
                        }
                    }
                }
            });
            this.downloader.setErrorYier((taskTag, e) -> {
                int stat = status.get();
                if (stat != STATUS_PAUSED && stat != STATUS_STOPPED) {
                    if (compareAndSetStatus(stat, STATUS_FAILED, e)) {
                        resetSpeed();
                        stopTimers();
                        stopAllWorkers();
                        notifyError(e);
                    }
                }
            });
        }

        private long getMaxAllowedDownloaded() {
            if (stopBoundary != -1) {
                return stopBoundary - startOffset + 1;
            } else if (endOffset != -1) {
                return endOffset - startOffset + 1;
            } else {
                return Long.MAX_VALUE;
            }
        }

        long getCurrentPos() {
            return startOffset + downloader.getDownloaded();
        }

        long getRemaining() {
            return stopBoundary == -1 ? 0 : stopBoundary - getCurrentPos();
        }
    }

    @Override
    public InspectResult inspect() throws IOException {
        return DownloadInspector.inspect(url, destFile, autoResolveName);
    }

    @Override
    public MultiThreadDownloadTask setAutoResolveName(boolean autoResolveName) {
        this.autoResolveName = autoResolveName;
        return this;
    }

    @Override
    public boolean isAutoResolveName() {
        return autoResolveName;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public DownloadFile getDestFile() {
        return destFile;
    }

    @Override
    public long getLength() {
        return totalLength;
    }

    @Override
    public MultiThreadDownloadTask setDownloaded(long downloaded) {
        this.totalDownloaded.set(downloaded);
        return this;
    }

    @Override
    public boolean pauseDownload() {
        while (true) {
            int stat = status.get();
            if (stat != STATUS_DOWNLOADING && stat != STATUS_STARTED) return false;
            if (compareAndSetStatus(stat, STATUS_PAUSED)) {
                resetSpeed();
                stopTimers();
                for (DownloadSegment segment : activeSegments) {
                    segment.downloader.pauseDownload();
                }
                countDownSyncLatch();
                return true;
            }
        }
    }

    public boolean resumeDownload() {
        int stat = status.get();
        if (stat == STATUS_PAUSED || stat == STATUS_FAILED) {
            if (activeSegments.isEmpty() && initialSegmentStatusses != null) {
                return false;
            }
            if (compareAndSetStatus(stat, STATUS_DOWNLOADING)) {
                if (stat == STATUS_FAILED) {
                    currentElapsedTime = 0;
                }
                syncLatch = new CountDownLatch(1);
                startTimers();
                bytesSinceLastSpeedCalculation.set(0);
                for (DownloadSegment segment : activeSegments) {
                    segment.downloader.resumeDownload();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void stopDownload() {
        int stat = status.get();
        if (stat != STATUS_COMPLETED && stat != STATUS_STOPPED && stat != STATUS_FAILED) {
            if (compareAndSetStatus(stat, STATUS_STOPPED)) {
                resetSpeed();
                stopTimers();
                stopAllWorkers();
                countDownSyncLatch();
            }
        }
    }

    @Override
    public long getDownloaded() {
        return totalDownloaded.get();
    }

    @Override
    public boolean hasStarted() {
        return status.get() != STATUS_IDLE;
    }

    @Override
    public boolean isInDownloading() {
        return status.get() == STATUS_DOWNLOADING;
    }

    @Override
    public boolean isStopped() {
        return status.get() == STATUS_STOPPED;
    }

    @Override
    public boolean isPaused() {
        return status.get() == STATUS_PAUSED;
    }

    @Override
    public boolean isCompleted() {
        return status.get() == STATUS_COMPLETED;
    }

    @Override
    public MultiThreadDownloadTask setTag(String tag) {
        this.tag = tag;
        return this;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    public MultiThreadDownloadTask setMaxRetryCount(int maxRetryCount) {
        if (maxRetryCount <= 0) throw new IllegalArgumentException("重试次数应该为正整数");
        this.maxRetryCount = maxRetryCount;
        return this;
    }

    @Override
    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    @Override
    public MultiThreadDownloadTask setHeader(String name, String value) {
        if (name != null && value != null) {
            this.headers.put(name, value);
        }
        return this;
    }

    @Override
    public MultiThreadDownloadTask setHeaders(Map<String, String> headers) {
        if (headers != null) {
            this.headers.putAll(headers);
        }
        return this;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public MultiThreadDownloadTask setProxy(Proxy proxy) {
        this.proxy = proxy;
        for (DownloadSegment segment : activeSegments) {
            segment.downloader.setProxy(proxy);
        }
        return this;
    }

    @Override
    public Proxy getProxy() {
        return proxy;
    }

    @Override
    public MultiThreadDownloadTask setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    @Override
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    @Override
    public int getStatus() {
        return status.get();
    }

    public MultiThreadDownloadTask setFallbackToSingleThread(boolean fallbackToSingleThread) {
        this.fallbackToSingleThread = fallbackToSingleThread;
        return this;
    }

    public boolean isFallbackToSingleThread() {
        return fallbackToSingleThread;
    }

    public MultiThreadDownloadTask setProgressYier(long intervalMs, ProgressYier progressYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("进度回调间隔必须大于等于 0 毫秒");
        this.progressIntervalMs = intervalMs;
        this.progressYier = progressYier;
        return this;
    }

    public MultiThreadDownloadTask setProgressYier(ProgressYier progressYier) {
        setProgressYier(0, progressYier);
        return this;
    }


    public MultiThreadDownloadTask setProgressIncreaseYier(long intervalMs, ProgressIncreaseYier progressIncreaseYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("进度增加回调间隔必须大于等于 0 毫秒");
        this.progressIncreaseIntervalMs = intervalMs;
        this.progressIncreaseYier = progressIncreaseYier;
        return this;
    }

    public MultiThreadDownloadTask setProgressIncreaseYier(ProgressIncreaseYier progressIncreaseYier) {
        return setProgressIncreaseYier(0, progressIncreaseYier);
    }

    public MultiThreadDownloadTask setSegmentProgressYier(long intervalMs, PartProgressYier partProgressYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("分片进度回调间隔必须大于等于 0 毫秒");
        this.segmentProgressIntervalMs = intervalMs;
        this.partProgressYier = partProgressYier;
        return this;
    }

    public MultiThreadDownloadTask setSegmentProgressYier(PartProgressYier partProgressYier) {
        setSegmentProgressYier(0, partProgressYier);
        return this;
    }

    public MultiThreadDownloadTask setErrorYier(ErrorYier errorYier) {
        this.errorYier = errorYier;
        return this;
    }

    public MultiThreadDownloadTask setStartYier(StartYier startYier) {
        this.startYier = startYier;
        return this;
    }

    public MultiThreadDownloadTask setCompletedYier(CompletedYier completedYier) {
        this.completedYier = completedYier;
        return this;
    }

    public MultiThreadDownloadTask setExitYier(ExitYier<TaskStates> exitYier) {
        this.exitYier = exitYier;
        return this;
    }
}