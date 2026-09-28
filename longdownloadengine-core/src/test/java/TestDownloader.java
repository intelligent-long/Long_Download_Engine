import com.longx.intelligent.lib.longdownloadengine.core.Inspect.InspectResult;
import com.longx.intelligent.lib.longdownloadengine.core.LongDownloadEngine;
import com.longx.intelligent.lib.longdownloadengine.core.client.HttpClient;
import com.longx.intelligent.lib.longdownloadengine.core.client.Proxy;
import com.longx.intelligent.lib.longdownloadengine.core.task.MultiThreadDownloadTask;
import com.longx.intelligent.lib.longdownloadengine.core.task.SingleThreadDownloadTask;
import com.longx.intelligent.lib.longdownloadengine.core.value.Value;
import com.longx.intelligent.lib.longdownloadengine.core.yier.ProgressYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.RemainingTimeYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.SpeedYier;
import com.longx.intelligent.lib.longdownloadengine.core.yier.StatusChangeYier;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Created by LONG on 2026/9/6 at 12:35.
 */
public class TestDownloader {
    private static String url = "https://download.internetdownloadmanager.com/idman643build10.exe";
    private static String directoryPath = "/Users/longtianxiang/Downloads";

    public static void main(String[] args) throws IOException {
        LongDownloadEngine.newDownloader(url, directoryPath)
                .setTag("TAG")
                .setFollowRedirects(true) // 重定向
                .setAutoResolveName(false) // 决定是使用现有文件，还是从响应头 \ URL 里解析
                .setHeader("", "") // 请求头
                .setProxy(new Proxy()) // 代理
                .setStatusChangeYier((tag, oldStatus, newStatus, e) -> {
                    // 状态回调
                    System.err.println(tag + " | 旧状态: " + oldStatus + " | 新状态: " + newStatus);
                })
                .setProgressYier(7, (tag, length, downloaded, progress) -> {
                    // 进度回调
                    System.err.println(tag + " | 总长度: " + length + " | 已下载: " + downloaded);
                })
                .setProgressIncreaseYier((tag, increase) -> {
                    // 进度增长回调
                    System.err.println(tag + " | 增长: " + increase);
                })
                .setSpeedYier(7, (tag, speed) -> {
                    // 速度回调
                    System.err.println(tag + " ｜ 速度: " + speed);
                })
                .setRemainingTimeYier(1000, (tag, remainingTime) -> {
                    // 剩余时间回调
                    System.err.println(tag + " ｜ 剩余时间: " + remainingTime);
                })
                .setErrorYier((tag, e) -> {
                    // 错误回调
                    System.err.println(tag + " ｜ 下载出错: " + e.getMessage());
                    e.printStackTrace();
                })
                .setCompletedYier(tag -> {
                    // 完成状态回调（也可在状态回调里监听）
                    System.err.println(tag + " ｜ 下载完成！");
                })
                .startDownload();

        SingleThreadDownloadTask singleThreadDownloadTask = LongDownloadEngine.newSingleThreadDownloadTask(url, directoryPath)
                .setTag("TAG")
                .setFollowRedirects(true) // 重定向
                .setAutoResolveName(false) // 决定是使用现有文件，还是从响应头 \ URL 里解析
                .setHeader("", "") // 请求头
                .setProxy(new Proxy()) // 代理
                .setMaxSpeed(10000) //速度限制
                .setStatusChangeYier((tag, oldStatus, newStatus, e) -> {
                    // 状态回调
                    System.err.println(tag + " | 旧状态: " + oldStatus + " | 新状态: " + newStatus);
                })
                .setProgressYier(7, (tag, length, downloaded, progress) -> {
                    // 进度回调
                    System.err.println(tag + " | 总长度: " + length + " | 已下载: " + downloaded);
                })
                .setProgressIncreaseYier((tag, increase) -> {
                    // 进度增长回调
                    System.err.println(tag + " | 增长: " + increase);
                })
                .setSpeedYier(7, (tag, speed) -> {
                    // 速度回调
                    System.err.println(tag + " ｜ 速度: " + speed);
                })
                .setRemainingTimeYier(1000, (tag, remainingTime) -> {
                    // 剩余时间回调
                    System.err.println(tag + " ｜ 剩余时间: " + remainingTime);
                })
                .setErrorYier((tag, e) -> {
                    // 错误回调
                    System.err.println(tag + " ｜ 下载出错: " + e.getMessage());
                    e.printStackTrace();
                })
                .setCompletedYier(tag -> {
                    // 完成状态回调（也可在状态回调里监听）
                    System.err.println(tag + " ｜ 下载完成！");
                });
        singleThreadDownloadTask.startDownloadSync(); // 同步下载
        SingleThreadDownloadTask.TaskStates taskStates = singleThreadDownloadTask.exit(); // 退出任务，导出任务数据

        MultiThreadDownloadTask multiThreadDownloadTask = LongDownloadEngine.newMultiThreadDownloadTask(url, directoryPath,
                        21) // 21 线程
                .setTag("TAG")
                .setFollowRedirects(true) // 重定向
                .setAutoResolveName(false) // 决定是使用现有文件，还是从响应头 \ URL 里解析
                .setHeader("", "") // 请求头
                .setProxy(new Proxy()) // 代理
                .setStatusChangeYier((tag, oldStatus, newStatus, e) -> {
                    // 状态回调
                    System.err.println(tag + " | 旧状态: " + oldStatus + " | 新状态: " + newStatus);
                })
                .setProgressYier(7, (tag, length, downloaded, progress) -> {
                    // 进度回调
                    System.err.println(tag + " | 总长度: " + length + " | 已下载: " + downloaded);
                })
                .setProgressIncreaseYier((tag, increase) -> {
                    // 进度增长回调
                    System.err.println(tag + " | 增长: " + increase);
                })
                .setSpeedYier(7, (tag, speed) -> {
                    // 速度回调
                    System.err.println(tag + " ｜ 速度: " + speed);
                })
                .setRemainingTimeYier(1000, (tag, remainingTime) -> {
                    // 剩余时间回调
                    System.err.println(tag + " ｜ 剩余时间: " + remainingTime);
                })
                .setErrorYier((tag, e) -> {
                    // 错误回调
                    System.err.println(tag + " ｜ 下载出错: " + e.getMessage());
                    e.printStackTrace();
                })
                .setCompletedYier(tag -> {
                    // 完成状态回调（也可在状态回调里监听）
                    System.err.println(tag + " ｜ 下载完成！");
                });
        multiThreadDownloadTask.startDownload();
        MultiThreadDownloadTask.TaskStates taskStates = multiThreadDownloadTask.exit(); // 退出任务，导出任务数据
        MultiThreadDownloadTask.buildUnfinishedInstance(taskStates); // 恢复状态

        InspectResult inspect = downloader.inspect(); // 检查下载文件数据

        // 总配置
        LongDownloadEngine.setClientConfigurator(builder -> {
            builder.connectTimeout(Value.CLIENT_CONNECT_TIMEOUT_SECOND, TimeUnit.SECONDS);
        });

        // 单独设置 HTTP Client
        LongDownloadEngine.newDownloader(url, directoryPath)
                .useOkHttpClient(null);

        // 配置当前 HTTP Client
        LongDownloadEngine.newDownloader(url, directoryPath)
                .configureOkHttpClient(builder -> {
                    // 配置
                });

        // 支持 Android，只需使用类型为 UriDownloadFile 的对象参数即可（类型已内置）
        LongDownloadEngine.newDownloader(url, new UriDownloadFile(context, uri, isTreeUri))
                .startDownload();
    }
}
