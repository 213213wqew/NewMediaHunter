package com.news.publish.desktop;

import com.news.publish.PublishCenterApplication;
import javafx.application.Application;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 桌面版入口：先启动 Spring Boot，再打开 WebView 窗口加载前端。
 * 运行此类即可以桌面程序方式使用（无需单独打开浏览器）。
 */
public class DesktopLauncher {

    private static final int PORT = 8080;
    private static final int WAIT_MS = 300;
    private static final int MAX_ATTEMPTS = 100;

    public static void main(String[] args) {
        Thread serverThread = new Thread(() -> {
            try {
                PublishCenterApplication.main(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        if (!waitForServer()) {
            System.err.println("后端未能在预期时间内启动，请检查端口 " + PORT + " 是否被占用");
            return;
        }

        Application.launch(WebViewLauncher.class, args);
    }

    private static boolean waitForServer() {
        String url = "http://localhost:" + PORT;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(500);
                conn.setReadTimeout(500);
                conn.getResponseCode();
                conn.disconnect();
                return true;
            } catch (IOException ignored) {
            }
            try {
                Thread.sleep(WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
