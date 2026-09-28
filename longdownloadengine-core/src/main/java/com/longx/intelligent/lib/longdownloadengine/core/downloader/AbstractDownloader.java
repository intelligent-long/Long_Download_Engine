package com.longx.intelligent.lib.longdownloadengine.core.downloader;

import com.longx.intelligent.lib.longdownloadengine.core.Inspect.DownloadInspector;
import com.longx.intelligent.lib.longdownloadengine.core.Inspect.InspectResult;
import com.longx.intelligent.lib.longdownloadengine.core.client.HttpClient;
import com.longx.intelligent.lib.longdownloadengine.core.client.Proxy;
import com.longx.intelligent.lib.longdownloadengine.core.io.DownloadFile;
import com.longx.intelligent.lib.longdownloadengine.core.io.SpeedLimiter;
import com.longx.intelligent.lib.longdownloadengine.core.value.Value;
import com.longx.intelligent.lib.longdownloadengine.core.yier.CompletedYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ErrorYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.StatusChangeYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.StartYier;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;

/**
 * Created by LONG on 2026/9/5 at 23:32.
 */
public abstract class AbstractDownloader<T extends AbstractDownloader<T>> implements DownloaderInterface<T> {
    protected String url;
    protected volatile DownloadFile destFile;
    protected volatile boolean autoResolveName = true;
    protected volatile String tag;
    protected volatile long length;
    protected final AtomicLong downloaded = new AtomicLong(0);
    protected volatile Call call;
    protected final AtomicInteger status = new AtomicInteger(STATUS_IDLE);
    protected volatile int maxRetryCount = Value.DEFAULT_DOWNLOADER_MAX_RETRY_COUNT;
    protected final AtomicInteger currentRetryCount = new AtomicInteger(0);
    protected volatile ErrorYier errorYier;
    protected volatile StartYier startYier;
    protected volatile CompletedYier completedYier;
    protected volatile StatusChangeYier statusChangeYier;
    protected final Map<String, String> headers = new ConcurrentHashMap<>();
    protected volatile Proxy proxy;
    protected volatile boolean followRedirects = true;
    protected volatile OkHttpClient okHttpClient;
    protected volatile OkHttpClient customOkHttpClient;
    protected volatile CountDownLatch syncLatch;
    protected volatile SpeedLimiter speedLimiter;
    protected volatile HttpClient.OkHttpClientConfigurator okHttpClientConfigurator;

    public AbstractDownloader(String url, DownloadFile destFile) {
        this.url = url;
        this.destFile = destFile;
    }

    public T configureOkHttpClient(HttpClient.OkHttpClientConfigurator configurator) {
        this.okHttpClientConfigurator = configurator;
        this.okHttpClient = null;
        return (T) this;
    }

    public T useOkHttpClient(OkHttpClient client) {
        this.customOkHttpClient = client;
        this.okHttpClient = null;
        return (T) this;
    }

    public OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            synchronized (this) {
                if (okHttpClient == null) {
                    OkHttpClient.Builder clientBuilder = (customOkHttpClient != null)
                            ? customOkHttpClient.newBuilder()
                            : HttpClient.getInstance().newBuilder();
                    if (proxy != null) {
                        clientBuilder.proxy(proxy.buildJavaNetProxy());
                        applyProxyAuthentication(clientBuilder, proxy);
                    }
                    clientBuilder.followRedirects(followRedirects);
                    clientBuilder.followSslRedirects(followRedirects);
                    if (okHttpClientConfigurator != null) {
                        okHttpClientConfigurator.configure(clientBuilder);
                    }
                    okHttpClient = clientBuilder.build();
                }
            }
        }
        return okHttpClient;
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
        return length;
    }

    @Override
    public T setHeader(String name, String value) {
        if (name != null && value != null) this.headers.put(name, value);
        return (T) this;
    }

    @Override
    public T setHeaders(Map<String, String> headers) {
        if (headers != null) this.headers.putAll(headers);
        return (T) this;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public synchronized T setProxy(Proxy proxy) {
        this.proxy = proxy;
        this.okHttpClient = null;
        currentRetryCount.set(0);
        cancelCall();
        return (T) this;
    }

    @Override
    public Proxy getProxy() {
        return proxy;
    }

    @Override
    public synchronized T setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        this.okHttpClient = null;
        return (T) this;
    }

    @Override
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public T setSpeedLimiter(SpeedLimiter speedLimiter) {
        this.speedLimiter = speedLimiter;
        return (T) this;
    }

    @Override
    public T setMaxSpeed(long maxBytesPerMs) {
        this.speedLimiter = new SpeedLimiter(maxBytesPerMs);
        return (T) this;
    }

    public SpeedLimiter getSpeedLimiter() {
        return speedLimiter;
    }

    public ErrorYier getErrorYier() {
        return errorYier;
    }

    public StartYier getStartYier() {
        return startYier;
    }

    public CompletedYier getCompletedYier() {
        return completedYier;
    }

    public StatusChangeYier getStatusChangeYier() {
        return statusChangeYier;
    }

    @Override
    public int getStatus() {
        return status.get();
    }

    @Override
    public InspectResult inspect() throws IOException {
        return DownloadInspector.inspect(url, destFile, autoResolveName);
    }

    public T setErrorYier(ErrorYier errorYier) {
        this.errorYier = errorYier;
        return (T) this;
    }

    public T setStartYier(StartYier startYier) {
        this.startYier = startYier;
        return (T) this;
    }

    public T setCompletedYier(CompletedYier completedYier) {
        this.completedYier = completedYier;
        return (T) this;
    }

    public T setStatusChangeYier(StatusChangeYier statusChangeYier) {
        this.statusChangeYier = statusChangeYier;
        if (this.statusChangeYier != null) {
            this.statusChangeYier.onStatusChanged(tag, -1, status.get(), null);
        }
        return (T) this;
    }

    @Override
    public T setTag(String tag) {
        this.tag = tag;
        return (T) this;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    public T setDownloaded(long downloaded) {
        this.downloaded.set(downloaded);
        return (T) this;
    }

    @Override
    public long getDownloaded() {
        return downloaded.get();
    }

    protected void cancelCall() {
        Call c = this.call;
        if (c != null && !c.isCanceled()) {
            c.cancel();
        }
    }

    protected void countDownSyncLatch() {
        CountDownLatch latch = this.syncLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    protected void notifyStatusChanged(int oldStatus, int newStatus, Exception error) {
        StatusChangeYier yier = this.statusChangeYier;
        if (yier != null) {
            yier.onStatusChanged(tag, oldStatus, newStatus, error);
        }
    }

    protected boolean changeStatus(int expected, int newStatus) {
        return changeStatus(expected, newStatus, null);
    }

    protected boolean changeStatus(int expected, int newStatus, Exception error) {
        if (status.compareAndSet(expected, newStatus)) {
            notifyStatusChanged(expected, newStatus, error);
            return true;
        }
        return false;
    }

    protected void changeStatus(int newStatus) {
        changeStatus(newStatus, null);
    }

    protected void changeStatus(int newStatus, Exception error) {
        int oldStatus = status.getAndSet(newStatus);
        if (oldStatus != newStatus) {
            notifyStatusChanged(oldStatus, newStatus, error);
        }
    }

    protected void notifyError(Exception e) {
        ErrorYier yier = this.errorYier;
        if (yier != null) {
            yier.onError(tag, e);
        }
        countDownSyncLatch();
    }

    protected void notifyStart() {
        StartYier yier = this.startYier;
        if (yier != null) {
            yier.onStart(tag, length);
        }
    }

    protected void notifyCompleted() {
        CompletedYier yier = this.completedYier;
        if (yier != null) {
            yier.onCompleted(tag);
        }
        countDownSyncLatch();
    }

    @Override
    public boolean pauseDownload() {
        while (true) {
            int s = status.get();
            if (s != STATUS_STARTED && s != STATUS_DOWNLOADING) {
                return false;
            }
            if (changeStatus(s, STATUS_PAUSED)) {
                cancelCall();
                countDownSyncLatch();
                return true;
            }
        }
    }

    @Override
    public boolean resumeDownload() {
        int s = status.get();
        if (s == STATUS_PAUSED || s == STATUS_FAILED) {
            currentRetryCount.set(0);
            return startDownload();
        }
        return false;
    }

    @Override
    public void stopDownload() {
        int s = status.get();
        if (s != STATUS_COMPLETED && s != STATUS_STOPPED && s != STATUS_FAILED) {
            changeStatus(STATUS_STOPPED);
            cancelCall();
            countDownSyncLatch();
        }
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
    public T setAutoResolveName(boolean autoResolveName) {
        this.autoResolveName = autoResolveName;
        return (T) this;
    }

    @Override
    public boolean isAutoResolveName() {
        return autoResolveName;
    }

    @Override
    public T setMaxRetryCount(int maxRetryCount) {
        if (maxRetryCount <= 0) {
            throw new IllegalArgumentException("重试次数应该为正整数");
        }
        this.maxRetryCount = maxRetryCount;
        return (T) this;
    }

    @Override
    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    protected boolean checkAndRetry(Exception lastException) {
        int current = status.get();
        if (current == STATUS_PAUSED || current == STATUS_STOPPED || current == STATUS_COMPLETED) {
            return false;
        }
        if (currentRetryCount.getAndIncrement() < maxRetryCount) {
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                int s = status.get();
                if (s != STATUS_PAUSED && s != STATUS_STOPPED) {
                    executeDownloadTask();
                }
            }, "Download-Retry-Thread").start();
            return true;
        } else {
            if (changeStatus(current, STATUS_FAILED, lastException)) {
                notifyError(lastException);
            }
            return false;
        }
    }

    protected abstract boolean executeDownloadTask();

    public static void applyProxyAuthentication(OkHttpClient.Builder clientBuilder, Proxy proxy) {
        if (proxy == null || proxy.buildJavaNetProxy().type() == java.net.Proxy.Type.DIRECT) {
            return;
        }
        String username = proxy.getUsername();
        String password = proxy.getPassword();
        if (proxy.buildJavaNetProxy().type() == java.net.Proxy.Type.HTTP) {
            if (username != null && !username.isBlank()) {
                clientBuilder.proxyAuthenticator((route, response) -> {
                    if (response.request().header("Proxy-Authorization") != null) {
                        return null;
                    }
                    String credential = Credentials.basic(username, password);
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                });
            }
        }
        else if (proxy.buildJavaNetProxy().type() == java.net.Proxy.Type.SOCKS) {
            if (username != null && !username.isBlank()) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() == Authenticator.RequestorType.PROXY) {
                            return new PasswordAuthentication(username, password.toCharArray());
                        }
                        return null;
                    }
                });
            }
        }
    }
}