package com.longx.intelligent.lib.longdownloadengine.core.Inspect;

import java.io.IOException;
import java.net.URLDecoder;

import com.longx.intelligent.lib.longdownloadengine.core.client.HttpClient;
import com.longx.intelligent.lib.longdownloadengine.core.io.DownloadFile;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by LONG on 2026/9/6 at 00:49.
 */
public class DownloadInspector {

    public static InspectResult inspect(String url) throws IOException {
        return inspect(url, null, false);
    }

    public static InspectResult inspect(String url, DownloadFile destFile) throws IOException {
        return inspect(url, destFile, false);
    }

    public static InspectResult inspect(String url, DownloadFile destFile, boolean autoResolveName) throws IOException {
        InspectResult result = new InspectResult();
        Request request = new Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .build();
        try (Response response = HttpClient.getInstance().newCall(request).execute()) {
            if (response.code() == 206) {
                result.supportRange = true;
                String contentRange = response.header("Content-Range");
                if (contentRange != null && contentRange.contains("/")) {
                    String length = contentRange.substring(contentRange.indexOf("/") + 1);
                    try {
                        result.length = Long.parseLong(length);
                    } catch (NumberFormatException e) {
                        result.length = -1;
                    }
                } else {
                    result.length = -1;
                }
            } else if (response.code() == 200) {
                result.supportRange = false;
                result.length = response.body().contentLength();
            } else {
                throw new IOException("请求失败，状态码：" + response.code());
            }
            String originalName = getFileName(response);
            result.originalName = originalName;
            DownloadFile resolvedFile = resolveDestFile(response, destFile, autoResolveName);
            result.resolvedDestFile = resolvedFile;
            result.resolvedName = resolvedFile != null ? resolvedFile.getName() : originalName;
        }
        return result;
    }

    public static DownloadFile resolveDestFile(Response response, DownloadFile destFile, boolean autoResolveName) {
        if (destFile == null) {
            return null;
        }
        if (autoResolveName) {
            String fileName = getFileName(response);
            if (destFile instanceof com.longx.intelligent.lib.longdownloadengine.core.io.JavaDownloadFile) {
                java.io.File dir = ((com.longx.intelligent.lib.longdownloadengine.core.io.JavaDownloadFile) destFile).getFile();
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            }
            return destFile.generateUniqueFile(fileName);
        } else {
            if (destFile.exists()) {
                return destFile.generateUniqueFile(destFile.getName());
            }
            return destFile;
        }
    }

    public static String getFileName(Response response) {
        String name = getFileNameFromHeader(response);
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return getFileNameFromUrl(response);
    }

    private static String getFileNameFromHeader(Response response) {
        String contentDisposition = response.header("Content-Disposition");
        if (contentDisposition == null || contentDisposition.isEmpty()) {
            return null;
        }
        String fileNameStar = extractParameter(contentDisposition, "filename*");
        if (fileNameStar != null && !fileNameStar.isEmpty()) {
            try {
                int index = fileNameStar.indexOf("''");
                if (index >= 0) {
                    fileNameStar = fileNameStar.substring(index + 2);
                }
                return URLDecoder.decode(fileNameStar, "UTF-8");
            } catch (Exception ignored) {
            }
        }
        String fileName = extractParameter(contentDisposition, "filename");
        if (fileName != null && !fileName.isEmpty()) {
            return fileName;
        }
        return null;
    }

    private static String extractParameter(String contentDisposition, String parameter) {
        String lower = contentDisposition.toLowerCase();
        String target = parameter.toLowerCase() + "=";
        int start = lower.indexOf(target);
        if (start < 0) {
            return null;
        }
        start += target.length();
        if (start >= contentDisposition.length()) {
            return null;
        }
        char firstChar = contentDisposition.charAt(start);
        if (firstChar == '"') {
            int end = contentDisposition.indexOf('"', start + 1);
            if (end < 0) {
                return null;
            }
            return contentDisposition.substring(start + 1, end);
        }
        int end = contentDisposition.indexOf(';', start);
        if (end < 0) {
            end = contentDisposition.length();
        }
        return contentDisposition.substring(start, end).trim();
    }

    private static String getFileNameFromUrl(Response response) {
        String path = response.request().url().encodedPath();
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == path.length() - 1) {
            return null;
        }
        String name = path.substring(slashIndex + 1);
        if (name.isEmpty()) {
            return null;
        }
        try {
            return URLDecoder.decode(name, "UTF-8");
        } catch (Exception ignored) {
            return name;
        }
    }
}