package com.news.publish.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * JavaFX 桌面壳：用 WebView 加载本机前端（Vue），
 * 与 Spring Boot 同进程运行时加载 http://localhost:8080。
 * 点击外链时在系统浏览器打开，本窗口保持当前页面。
 */
public class WebViewLauncher extends Application {

    private static final String APP_ORIGIN = "http://localhost:8080";
    private static final int MIN_WIDTH = 1280;
    private static final int MIN_HEIGHT = 800;

    private final StringBuilder lastAppUrl = new StringBuilder(APP_ORIGIN);

    @Override
    public void start(Stage primaryStage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        engine.locationProperty().addListener((ChangeListener<String>) (obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) return;
            if (isAppUrl(newVal)) {
                lastAppUrl.setLength(0);
                lastAppUrl.append(newVal);
                return;
            }
            getHostServices().showDocument(newVal);
            Platform.runLater(() -> engine.load(lastAppUrl.toString()));
        });

        engine.load(APP_ORIGIN);

        Scene scene = new Scene(webView, MIN_WIDTH, MIN_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setTitle("新闻发布中心");
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setOnCloseRequest(e -> Platform.exit());

        primaryStage.show();
    }

    private boolean isAppUrl(String url) {
        return url.startsWith(APP_ORIGIN);
    }
}
