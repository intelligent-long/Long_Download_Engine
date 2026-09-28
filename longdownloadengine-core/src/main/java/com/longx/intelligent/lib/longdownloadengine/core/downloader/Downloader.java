package com.longx.intelligent.lib.longdownloadengine.core.downloader;

import com.longx.intelligent.lib.longdownloadengine.core.Inspect.DownloadInspector;
import com.longx.intelligent.lib.longdownloadengine.core.io.DownloadFile;
import com.longx.intelligent.lib.longdownloadengine.core.io.JavaDownloadFile;
import com.longx.intelligent.lib.longdownloadengine.core.io.RandomAccessOutput;
import com.longx.intelligent.lib.longdownloadengine.core.io.SpeedLimiter;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ProgressIncreaseYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ProgressYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.RemainingTimeYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.SpeedYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ElapsedTimeYier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.jetbrains.annotations.NotNull;

/**
 * Created by LONG on 2026/9/6 at 00:29.
 */
public class Downloader extends AbstractDownloader<Downloader> {
    private volatile ProgressYier progressYier;
    private volatile long progressIntervalMs = 0;
    private volatile long lastProgressCallbackTime = 0;
    private volatile ProgressIncreaseYier progressIncreaseYier;
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
    private volatile boolean callbackRemainingTimeBeforeWindowFilled = true;
    private volatile long progressIncreaseIntervalMs = 0;
    private volatile long lastProgressIncreaseCallbackTime = 0;
    private final AtomicLong accumulatedProgressIncrease = new AtomicLong(0);

    public Downloader(String url, DownloadFile destFile) {
        super(url, destFile);
    }

    public Downloader(String url, File destFile) {
        super(url, new JavaDownloadFile(destFile));
    }

    public Downloader(String url, String destFilePath) {
        super(url, new JavaDownloadFile(new File(destFilePath)));
    }

    public Downloader setProgressYier(long intervalMs, ProgressYier progressYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("进度回调间隔必须大于等于 0 毫秒");
        this.progressIntervalMs = intervalMs;
        this.progressYier = progressYier;
        return this;
    }

    public Downloader setProgressYier(ProgressYier progressYier) {
        setProgressYier(0, progressYier);
        return this;
    }

    public Downloader setProgressIncreaseYier(long intervalMs, ProgressIncreaseYier progressIncreaseYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("进度增加回调间隔必须大于等于 0 毫秒");
        this.progressIncreaseIntervalMs = intervalMs;
        this.progressIncreaseYier = progressIncreaseYier;
        return this;
    }

    public Downloader setProgressIncreaseYier(ProgressIncreaseYier progressIncreaseYier) {
        return setProgressIncreaseYier(0, progressIncreaseYier);
    }

    public Downloader setSpeedYier(long intervalMs, SpeedYier speedYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("速度计算间隔必须大于等于 0 毫秒");
        this.speedIntervalMs = intervalMs;
        this.speedYier = speedYier;
        return this;
    }

    public long getSpeed() {
        return currentSpeed;
    }

    public Downloader setRemainingTimeYier(long intervalMs, boolean callbackBeforeWindowFilled, RemainingTimeYier remainingTimeYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("剩余时间计算间隔必须大于等于 0 毫秒");
        this.remainingTimeIntervalMs = intervalMs;
        this.callbackRemainingTimeBeforeWindowFilled = callbackBeforeWindowFilled;
        this.remainingTimeYier = remainingTimeYier;
        return this;
    }

    public Downloader setRemainingTimeYier(long intervalMs, RemainingTimeYier remainingTimeYier) {
        return setRemainingTimeYier(intervalMs, true, remainingTimeYier);
    }

    public Downloader setAverageSpeedWindowMs(long averageSpeedWindowMs) {
        if (averageSpeedWindowMs <= 0) throw new IllegalArgumentException("平均速度窗口必须大于 0 毫秒");
        this.averageSpeedWindowMs = averageSpeedWindowMs;
        return this;
    }

    public long getRemainingTime() {
        return currentRemainingTime;
    }

    public Downloader setElapsedTimeYier(long intervalMs, ElapsedTimeYier elapsedTimeYier) {
        if (intervalMs < 0) throw new IllegalArgumentException("已用时间计算间隔必须大于等于 0 毫秒");
        this.elapsedTimeIntervalMs = intervalMs;
        this.elapsedTimeYier = elapsedTimeYier;
        return this;
    }

    public long getElapsedTime() {
        return currentElapsedTime;
    }

    public ProgressYier getProgressYier() {
        return progressYier;
    }

    public SpeedYier getSpeedYier() {
        return speedYier;
    }

    public RemainingTimeYier getRemainingTimeYier() {
        return remainingTimeYier;
    }

    public ElapsedTimeYier getElapsedTimeYier() {
        return elapsedTimeYier;
    }

    private synchronized void startTimers() {
        stopTimers();
        timerScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Downloader-Timer");
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
                        long currentTotalLength = length;
                        long currentDownloadedBytes = downloaded.get();
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

    private void resetSpeedAndCallIntervalYiers() {
        currentSpeed = 0;
        currentRemainingTime = -1;
        bytesSinceLastSpeedCalculation.set(0);
        downloadProgressHistory.clear();
        lastProgressCallbackTime = 0;
        SpeedYier speedYie = speedYier;
        if (speedYie != null) speedYie.onSpeed(tag, 0);
        RemainingTimeYier remainingTimeYie = remainingTimeYier;
        if (remainingTimeYie != null) remainingTimeYie.timeRemaining(tag, -1);
        ElapsedTimeYier elapsedTimeYie = elapsedTimeYier;
        if (elapsedTimeYie != null) elapsedTimeYie.timeElapsed(tag, currentElapsedTime);
    }

    @Override
    public void stopDownload() {
        super.stopDownload();
        resetSpeedAndCallIntervalYiers();
        stopTimers();
    }

    @Override
    public boolean startDownload() {
        while (true) {
            int stat = status.get();
            if (stat == STATUS_STARTED || stat == STATUS_DOWNLOADING) return false;
            if (changeStatus(stat, STATUS_STARTED)) {
                if (stat == STATUS_FAILED) {
                    currentElapsedTime = 0;
                }
                return executeDownloadTask();
            }
        }
    }

    @Override
    public boolean startDownloadSync() {
        CountDownLatch latch = new CountDownLatch(1);
        this.syncLatch = latch;
        if (!startDownload()) {
            this.syncLatch = null;
            return false;
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            stopDownload();
            Thread.currentThread().interrupt();
            return false;
        } finally {
            this.syncLatch = null;
        }
        return isCompleted();
    }

    @Override
    protected boolean executeDownloadTask() {
        startTimers();
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (!headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }
        if (downloaded.get() > 0) {
            requestBuilder.header("Range", "bytes=" + downloaded.get() + "-");
        }
        call = getOkHttpClient().newCall(requestBuilder.build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                resetSpeedAndCallIntervalYiers();
                stopTimers();
                int stat = status.get();
                if (stat != STATUS_PAUSED && stat != STATUS_STOPPED) {
                    checkAndRetry(e);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (Response respons = response) {
                    int stat = status.get();
                    if (stat == STATUS_PAUSED || stat == STATUS_STOPPED) {
                        resetSpeedAndCallIntervalYiers();
                        stopTimers();
                        return;
                    }
                    if (!respons.isSuccessful() && respons.code() != 416) {
                        int code = respons.code();
                        resetSpeedAndCallIntervalYiers();
                        stopTimers();
                        checkAndRetry(new IOException("HTTP 请求失败，状态码: " + code));
                        return;
                    }
                    if (downloaded.get() == 0) {
                        DownloadFile targetFile = destFile;
                        targetFile = DownloadInspector.resolveDestFile(respons, targetFile, autoResolveName);
                        destFile = targetFile;
                    }
                    if (destFile != null) destFile.createDirs();
                    int code = respons.code();
                    ResponseBody body = respons.body();
                    long contentLength = body.contentLength();
                    try (InputStream is = body.byteStream();
                         RandomAccessOutput raf = destFile.openForWrite()) {
                        if (code == 416) {
                            if (downloaded.get() > 0 && (length <= 0 || downloaded.get() == length)) {
                                if (changeStatus(stat, STATUS_COMPLETED) || changeStatus(STATUS_STARTED, STATUS_COMPLETED) || changeStatus(STATUS_DOWNLOADING, STATUS_COMPLETED)) {
                                    resetSpeedAndCallIntervalYiers();
                                    stopTimers();
                                    notifyCompleted();
                                }
                                return;
                            }
                            downloaded.set(0);
                            raf.setLength(0);
                            resetSpeedAndCallIntervalYiers();
                            stopTimers();
                            checkAndRetry(new IOException("HTTP 416 Range Not Satisfiable"));
                            return;
                        } else if (code == 206) {
                            length = (contentLength == -1) ? -1 : downloaded.get() + contentLength;
                            raf.seek(downloaded.get());
                        } else if (code == 200) {
                            downloaded.set(0);
                            length = contentLength;
                            raf.setLength(0);
                            raf.seek(0);
                        }
                        notifyStart();
                        changeStatus(STATUS_DOWNLOADING);
                        currentRetryCount.set(0);
                        byte[] buffer = new byte[64 * 1024];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            int currStatus = status.get();
                            if (currStatus == STATUS_PAUSED || currStatus == STATUS_STOPPED) break;
                            SpeedLimiter limiter = getSpeedLimiter();
                            if (limiter != null) {
                                limiter.acquire(len);
                            }
                            raf.write(buffer, 0, len);
                            if (speedYier != null || remainingTimeYier != null) {
                                bytesSinceLastSpeedCalculation.addAndGet(len);
                            }
                            long currentDownloaded = downloaded.addAndGet(len);
                            ProgressIncreaseYier incYier = progressIncreaseYier;
                            if (incYier != null) {
                                if (progressIncreaseIntervalMs <= 0) {
                                    incYier.onProgressIncrease(tag, len);
                                } else {
                                    accumulatedProgressIncrease.addAndGet(len);
                                    long now = System.currentTimeMillis();
                                    boolean isComplete = (length > 0 && currentDownloaded >= length);
                                    if (isComplete || (now - lastProgressIncreaseCallbackTime >= progressIncreaseIntervalMs)) {
                                        lastProgressIncreaseCallbackTime = now;
                                        long bytesToCallback = accumulatedProgressIncrease.getAndSet(0);
                                        if (bytesToCallback > 0) {
                                            incYier.onProgressIncrease(tag, bytesToCallback);
                                        }
                                    }
                                }
                            }
                            ProgressYier progYier = progressYier;
                            if (progYier != null) {
                                long now = System.currentTimeMillis();
                                boolean isComplete = (length > 0 && currentDownloaded >= length);
                                if (isComplete || (now - lastProgressCallbackTime >= progressIntervalMs)) {
                                    lastProgressCallbackTime = now;
                                    double progress = 0;
                                    if (length > 0) {
                                        progress = Math.min(1.0, (double) currentDownloaded / length);
                                    }
                                    progYier.onProgress(tag, length, currentDownloaded, progress);
                                }
                            }
                        }
                        if (progressIncreaseYier != null && progressIncreaseIntervalMs > 0) {
                            long remainingBytes = accumulatedProgressIncrease.getAndSet(0);
                            if (remainingBytes > 0) {
                                progressIncreaseYier.onProgressIncrease(tag, remainingBytes);
                            }
                        }
                        resetSpeedAndCallIntervalYiers();
                        stopTimers();
                        if (changeStatus(STATUS_DOWNLOADING, STATUS_COMPLETED)) {
                            notifyCompleted();
                        }
                    }
                } catch (Exception e) {
                    resetSpeedAndCallIntervalYiers();
                    stopTimers();
                    int currStatus = status.get();
                    if (currStatus != STATUS_PAUSED && currStatus != STATUS_STOPPED) {
                        checkAndRetry(new Exception("下载过程中发生异常", e));
                    }
                }
            }
        });
        return true;
    }

    @Override
    public boolean pauseDownload() {
        boolean paused = super.pauseDownload();
        if (paused) {
            resetSpeedAndCallIntervalYiers();
            stopTimers();
        }
        return paused;
    }
}