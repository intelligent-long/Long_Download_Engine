package com.longx.intelligent.lib.longdownloadengine.core.client;

import java.util.concurrent.TimeUnit;
import com.longx.intelligent.lib.longdownloadengine.core.value.Value;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

/**
 * Created by LONG on 2026/9/6 at 00:16.
 */
public class HttpClient {
    private static volatile OkHttpClient INSTANCE;
    private static OkHttpClientConfigurator configurator;

    public interface OkHttpClientConfigurator {
        void configure(OkHttpClient.Builder builder);
    }

    private HttpClient() {}

    public static void setConfigurator(OkHttpClientConfigurator customConfigurator) {
        configurator = customConfigurator;
    }

    public static OkHttpClient getInstance() {
        if (INSTANCE == null) {
            synchronized (HttpClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = createClient();
                }
            }
        }
        return INSTANCE;
    }

    private static OkHttpClient createClient() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(Value.CLIENT_MAX_REQUESTS);
        dispatcher.setMaxRequestsPerHost(Value.CLIENT_MAX_REQUESTS_PER_HOST);
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(Value.CLIENT_CONNECT_TIMEOUT_SECOND, TimeUnit.SECONDS)
                .readTimeout(Value.CLIENT_READ_TIMEOUT_SECOND, TimeUnit.SECONDS)
                .writeTimeout(Value.CLIENT_WRITE_TIMEOUT_SECOND, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        if (configurator != null) {
            configurator.configure(builder);
        }
        return builder.build();
    }
}