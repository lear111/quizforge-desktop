package io.quizforge.desktop.browser.webview2;

/** Plain JVM entry avoids the launcher's JavaFX module-path special case. */
public final class WebView2Entry {
    private WebView2Entry() { }
    public static void main(String[] args) {
        var flags=java.util.List.of(args);
        javafx.application.Application.launch(flags.contains("--history")?WebView2HistoryLauncher.class:flags.contains("--editor")?WebView2EditorLauncher.class:flags.contains("--product")?WebView2ProductLauncher.class:WebView2PracticeLauncher.class,args);
        if(java.util.List.of(args).contains("--verify")&&!WebView2Verification.passed)System.exit(1);
    }
}
