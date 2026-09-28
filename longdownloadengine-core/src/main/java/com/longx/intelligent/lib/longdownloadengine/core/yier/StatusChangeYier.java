package com.longx.intelligent.lib.longdownloadengine.core.yier;

/**
 * Created by LONG on 2026/9/19 at 下午11:26.
 */
public interface StatusChangeYier {
    void onStatusChanged(String tag, int oldStatus, int newStatus, Exception e);
}
