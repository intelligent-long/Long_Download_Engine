# Long Download Engine
被精神分裂症（树苗嫁接）开发的 高性能、功能丰富、支持多线程负载均衡的 Java \ Android 下载引擎库

## 特性
1. 支持单线程下载
2. 支持多线程负载均衡下载，动态下载分担与调整
3. 支持暂停 \ 恢复 \ 停止 \ 退出
5. 支持状态回调，进度回调，速度回调，剩余时间回调，动态计算数据，并可自定义回调间隔时间
6. 支持下载速度限制
7. 支持自定义请求头
8. 支持自定义代理
9. 支持全部下载和部分下载（通过 Range 头部）
10. 支持下载任务管理，可退出下载 \ 从退出状态恢复下载
11. 支持同步 \ 异步下载
12. 支持 Android

## 使用示例
### 普通下载器
```java
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
                .setProgressIncreaseYier(7, (tag, increase) -> {
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
```

### 单线程下载任务
```java
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
                .setProgressIncreaseYier(7, (tag, increase) -> {
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
```

### 多线程下载任务
```java
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
                .setProgressIncreaseYier(7, (tag, increase) -> {
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
```

### 下载前检查
```java
        InspectResult inspect = downloader.inspect(); // 检查下载文件数据
```

### 配置 HTTP Client
```java
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
```

### Android
```java
        // 支持 Android，只需使用类型为 UriDownloadFile 的对象参数即可（类型已内置）
        LongDownloadEngine.newDownloader(url, new UriDownloadFile(context, uri, isTreeUri))
                .startDownload();
```
