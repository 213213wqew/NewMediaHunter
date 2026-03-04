package com.news.publish;

import com.news.publish.desktop.WebViewLauncher;
import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class PublishCenterApplication {

    private static final int PORT = 8080;
    private static final int WAIT_MS = 200;
    private static final int MAX_ATTEMPTS = 75;

    public static void main(String[] args) {
        Thread serverThread = new Thread(() -> SpringApplication.run(PublishCenterApplication.class, args));
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
