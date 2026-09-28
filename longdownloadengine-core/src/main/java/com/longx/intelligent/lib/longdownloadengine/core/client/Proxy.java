package com.longx.intelligent.lib.longdownloadengine.core.client;

import java.net.InetSocketAddress;

/**
 * Created by LONG on 2026/9/14 at 下午9:12.
 */
public class Proxy {
    public enum Type { HTTP, SOCKS }

    private Type type;
    private String host;
    private int port;
    private String username;
    private String password;

    public Proxy() {
    }

    public Proxy(Type type, String host, int port, String username, String password) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public java.net.Proxy buildJavaNetProxy() {
        if (host == null || host.isBlank() || port <= 0) {
            return java.net.Proxy.NO_PROXY;
        }
        java.net.Proxy.Type netType = (type == Type.HTTP) ? java.net.Proxy.Type.HTTP : java.net.Proxy.Type.SOCKS;
        return new java.net.Proxy(netType, new InetSocketAddress(host, port));
    }

    public Type getType() { return type; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
}