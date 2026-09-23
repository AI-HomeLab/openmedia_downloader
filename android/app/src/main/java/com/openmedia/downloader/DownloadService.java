package com.openmedia.downloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 前景下載服務：通知列進度 + 取消按鈕。單 flight（一次只跑一個下載）。
 * 取消是 best-effort：底層同步呼叫殺不掉時，結果丟棄、狀態照走 CANCELLED，
 * 保證冪等不 crash（見 ticket 02）。
 */
public class DownloadService extends Service {
    public static final String ACTION_DOWNLOAD =
            "com.openmedia.downloader.action.DOWNLOAD";
    public static final String ACTION_CANCEL =
            "com.openmedia.downloader.action.CANCEL";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_FORMAT = "format";
    public static final String EXTRA_KIND = "kind";

    private static final String CHANNEL_ID = "download";
    private static final int NOTIFICATION_ID = 1;

    /** Plugin 側掛的監聽（主執行緒回呼由呼叫方保證）。 */
    public interface Listener {
        void onProgress(float percent, long etaSeconds, long speedBps);

        void onDone(Uri fileUri, String fileName, boolean merged);

        void onError(DownloadError code, String message);
    }

    private static volatile Listener listener;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> current;

    public static void setListener(Listener l) {
        listener = l;
    }

    public static boolean isRunning() {
        return running.get();
    }

    /** 已在跑就拒絕（回 false），呼叫方報 BUSY。 */
    public static boolean startDownload(Context context, String url, String format) {
        return startDownload(context, url, format, "video");
    }

    /** 已在跑就拒絕（回 false），呼叫方報 BUSY。 */
    public static boolean startDownload(Context context, String url, String format, String kind) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        cancelFlag.set(false);
        Intent intent = new Intent(context, DownloadService.class)
                .setAction(ACTION_DOWNLOAD)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_FORMAT, format)
                .putExtra(EXTRA_KIND, kind);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        return true;
    }

    /** 冪等：沒在跑也安全。 */
    public static void cancel() {
        cancelFlag.set(true);
        Context app = AppHolder.app;
        if (app != null) {
            app.startService(new Intent(app, DownloadService.class)
                    .setAction(ACTION_CANCEL));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppHolder.app = getApplicationContext();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(intent.getAction())) {
            finishCancelled();
            return START_NOT_STICKY;
        }
        if (ACTION_DOWNLOAD.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            String format = intent.getStringExtra(EXTRA_FORMAT);
            String kind = intent.getStringExtra(EXTRA_KIND);
            startForeground(NOTIFICATION_ID, buildNotification(0, "準備下載…"));
            current = executor.submit(() -> runDownload(url, format, kind));
        }
        return START_NOT_STICKY;
    }

    private AudioTranscoder transcoder = new FFmpegAudioTranscoder();

    private void runDownload(String url, String format, String kind) {
        File staging = new File(getCacheDir(), "dl-" + System.currentTimeMillis());
        try {
            File landed = Downloader.download(this, url, staging, format, kind,
                    (percent, eta, speed, line) -> {
                        if (cancelFlag.get()) {
                            throw new DownloadCancelled();
                        }
                        updateNotification((int) percent, eta, speed);
                        Listener l = listener;
                        if (l != null) {
                            l.onProgress(percent, eta, speed);
                        }
                    }, cancelFlag);
            if (cancelFlag.get()) {
                deleteQuietly(staging);
                finishCancelled();
                return;
            }
            File finalFile = landed;
            boolean processed = true;
            if ("audio".equals(kind)) {
                // bestaudio 下載檔 → mp3；失敗走 POSTPROCESS＋留原檔（與合併同一語意）。
                // Throwable 全收（含 native 載入失敗）：轉檔永遠不能把整單拖成懸空。
                try {
                    finalFile = transcoder.transcode(landed);
                } catch (DownloadException e) {
                    if (e.getPartialFile() != null) {
                        finalFile = e.getPartialFile();
                    }
                    processed = false;
                } catch (Throwable t) {
                    android.util.Log.e("DownloadService", "transcode 意外失敗，留原檔", t);
                    processed = false;
                }
            }
            Uri uri = MediaStoreSaver.save(this, finalFile);
            deleteQuietly(staging);
            running.set(false);
            stopForeground(true);
            stopSelf();
            Listener l = listener;
            if (l != null) {
                l.onDone(uri, MediaStoreSaver.displayName(this, uri), processed);
            }
        } catch (DownloadCancelled e) {
            finishCancelled();
        } catch (DownloadException e) {
            // 合併失敗但有原檔：存原檔並以「未合併」完成（ticket 03 語意）。
            if (e.getCode() == DownloadError.POSTPROCESS && e.getPartialFile() != null) {
                try {
                    Uri uri = MediaStoreSaver.save(this, e.getPartialFile());
                    deleteQuietly(staging);
                    running.set(false);
                    stopForeground(true);
                    stopSelf();
                    Listener l = listener;
                    if (l != null) {
                        l.onDone(uri, MediaStoreSaver.displayName(this, uri), false);
                    }
                    return;
                } catch (DownloadException ignored) {
                    // 存都存不進去才往下走一般錯誤路；記一筆免得無聲。
                    android.util.Log.w("DownloadService",
                            "partial save failed: " + ignored.getMessage());
                }
            }
            // 一般錯誤路不留半成品（POSTPROCESS 分支已先存走原檔）。
            deleteQuietly(staging);
            running.set(false);
            stopForeground(true);
            stopSelf();
            Listener l = listener;
            if (l != null) {
                l.onError(e.getCode(), e.getMessage());
            }
        }
    }

    private void finishCancelled() {
        // 注意：cancelFlag 在這裡不清掉，下一次 startDownload 才清。
        // 飛行中的執行緒結束時靠它丟棄結果；若在這裡清掉會跟執行緒打架，
        // 取消照存檔（實測抓到的 bug）。
        running.set(false);
        if (current != null) {
            current.cancel(true); // 底層若還在跑，結果會被丟棄
        }
        stopForeground(true);
        stopSelf();
        Listener l = listener;
        if (l != null) {
            l.onError(DownloadError.CANCELLED, "已取消");
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "下載", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(int percent, String text) {
        Intent cancel = new Intent(this, DownloadService.class).setAction(ACTION_CANCEL);
        PendingIntent action = PendingIntent.getService(
                this, 0, cancel, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OpenMedia 下載中")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, percent, percent <= 0)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", action)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(int percent, long eta, long speedBps) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            String text = percent >= 0 ? percent + "%・ETA " + eta + "s" : "下載中…";
            String speed = speedBps > 0 ? "・" + formatSpeed(speedBps) : "";
            nm.notify(NOTIFICATION_ID, buildNotification(Math.max(percent, 0), text + speed));
        }
    }

    static String formatSpeed(long bps) {
        double kb = bps / 1024.0;
        if (kb < 1024) {
            return String.format(java.util.Locale.US, "%.0f KB/s", kb);
        }
        return String.format(java.util.Locale.US, "%.1f MB/s", kb / 1024);
    }

    private static void deleteQuietly(File dir) {
        if (dir == null) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        dir.delete();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    /** 記 application context 供靜態 cancel() 用（service 存活期內有效）。 */
    static class AppHolder {
        static Context app;
    }
}
