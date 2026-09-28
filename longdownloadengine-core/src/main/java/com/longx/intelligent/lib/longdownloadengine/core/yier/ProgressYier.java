package com.longx.intelligent.lib.longdownloadengine.core.yier;

/**
 * Created by LONG on 2026/9/6 at 00:25.
 */
public interface ProgressYier {
    void onProgress(String tag, long length, long downloaded, double progress);
}
