package com.longx.intelligent.lib.longdownloadengine.core.Inspect;

import com.longx.intelligent.lib.longdownloadengine.core.io.DownloadFile;

/**
 * Created by LONG on 2026/9/6 at 02:02.
 */
public class InspectResult {
    protected boolean supportRange;
    protected long length;
    protected String originalName;
    protected String resolvedName;
    protected DownloadFile resolvedDestFile;

    public boolean isSupportRange() {
        return supportRange;
    }

    public long getLength() {
        return length;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getResolvedName() {
        return resolvedName;
    }

    public DownloadFile getResolvedDestFile() {
        return resolvedDestFile;
    }

    @Override
    public String toString() {
        return "InspectResult{" +
                "supportRange=" + supportRange +
                ", length=" + length +
                ", originalName='" + originalName + '\'' +
                ", resolvedName='" + resolvedName + '\'' +
                ", resolvedDestFile=" + resolvedDestFile +
                '}';
    }
}