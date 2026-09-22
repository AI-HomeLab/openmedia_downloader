package com.openmedia.downloader;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Pre-Capacitor 佔位 Activity：證明 Gradle 骨架可編譯、可打包。
 * 接入 Capacitor 後會換成 BridgeActivity（由 cap add 產生），此檔到時移除或改寫。
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView view = new TextView(this);
        view.setText("OpenMedia — scaffold");
        view.setTextSize(24);
        setContentView(view);
    }
}
