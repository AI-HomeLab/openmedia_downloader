package com.openmedia.downloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public static final String ACTION_BATCH =
            "com.openmedia.downloader.action.BATCH";
    public static final String ACTION_CANCEL =
            "com.openmedia.downloader.action.CANCEL";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_FORMAT = "format";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_MAX_HEIGHT = "maxHeight";
    public static final String EXTRA_OVERRIDES = "overrides";

    private static final String CHANNEL_ID = "download";
    private static final int NOTIFICATION_ID = 1;

    /**
     * API 29+ 前景宣告：三參數版（帶 dataSync type）。manifest 已宣告同 type＋權限；
     * 之前兩參數版在 API 35 上約 3 秒被系統收回（Stop FGS timeout，下載靠
     * worker 續命但通知消失；見 ticket 08）。
     */
    private void startForegroundTyped(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    /** Plugin 側掛的監聽（主執行緒回呼由呼叫方保證）。 */
    public interface Listener {
        void onProgress(float percent, long etaSeconds, long speedBps);

        void onDone(Uri fileUri, String fileName, boolean merged);

        void onError(DownloadError code, String message);

        /** 整批進度（ticket 06）：index 從 0 起；單下流程不呼叫。 */
        default void onBatchProgress(int index, int total, String itemTitle,
                                     float percent, long etaSeconds, long speedBps) {
        }

        /** 整批結束：成功數＋單項失敗清單（全成功時 failed 為空）。 */
        default void onBatchDone(int succeeded, int total,
                                 java.util.List<BatchFailure> failed) {
        }
    }

    private static volatile Listener listener;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> current;
    /** 整批迴圈是否在跑：是的話取消走檢查點，不 interrupt（見 onStartCommand）。 */
    private volatile boolean batchActive = false;

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

    /** 整批：已在跑就拒收（回 false），呼叫方報 BUSY。 */
    public static boolean startBatch(Context context, String playlistUrl, String kind,
                                     int maxHeight, String overridesJson) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        cancelFlag.set(false);
        Intent intent = new Intent(context, DownloadService.class)
                .setAction(ACTION_BATCH)
                .putExtra(EXTRA_URL, playlistUrl)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_MAX_HEIGHT, maxHeight)
                .putExtra(EXTRA_OVERRIDES, overridesJson);
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
            if (batchActive) {
                // 整批取消走迴圈檢查點（項間停＋當項半成品刪除＋已完成 N/M 交代）；
                // 這裡不再 interrupt，避免中斷 Python/ffmpeg 搞壞狀態。
                // cancelFlag 已由 cancel() 設好。
                return START_NOT_STICKY;
            }
            finishCancelled();
            return START_NOT_STICKY;
        }
        if (ACTION_DOWNLOAD.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            String format = intent.getStringExtra(EXTRA_FORMAT);
            String kind = intent.getStringExtra(EXTRA_KIND);
            startForegroundTyped(buildNotification(0, "準備下載…"));
            current = executor.submit(() -> runDownload(url, format, kind));
        }
        if (ACTION_BATCH.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            String kind = intent.getStringExtra(EXTRA_KIND);
            int maxHeight = intent.getIntExtra(EXTRA_MAX_HEIGHT, 1080);
            String overrides = intent.getStringExtra(EXTRA_OVERRIDES);
            startForegroundTyped(buildNotification(0, "準備整批下載…"));
            current = executor.submit(() -> runBatch(url, kind, maxHeight, overrides));
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

    /**
     * 整批：逐項 resolve→下載→存檔，單項失敗記下繼續跑（ticket 06）。
     * 取消停在當項：項間檢查直接停，項內中斷走 CANCELLED，都不留半成品。
     */
    private void runBatch(String playlistUrl, String kind, int maxHeight, String overridesJson) {
        batchActive = true;
        Map<String, Integer> overrides = parseOverrides(overridesJson);
        boolean audioMode = "audio".equals(kind);
        List<BatchFailure> failed = new ArrayList<>();
        int succeeded = 0;
        int total = 0;
        try {
            PlaylistResult list;
            try {
                list = ResolveEngine.resolvePlaylist(this, playlistUrl);
            } catch (ResolveException e) {
                throw new DownloadException(e.getCode(), e.getMessage(), e);
            }
            total = list.items.size();
            for (int i = 0; i < list.items.size(); i++) {
                if (cancelFlag.get()) {
                    finishBatchCancelled(succeeded, total);
                    return;
                }
                PlaylistItem item = list.items.get(i);
                int h = overrides.getOrDefault(item.videoId, maxHeight);
                emitBatch(i, total, item.title, 0, 0, 0);
                try {
                    downloadBatchItem(item, audioMode, h, i, total);
                    succeeded++;
                } catch (DownloadCancelled e) {
                    finishBatchCancelled(succeeded, total);
                    return;
                } catch (DownloadException e) {
                    failed.add(new BatchFailure(i, item.videoId, item.title,
                            e.getCode(), e.getMessage()));
                }
                emitBatch(i, total, item.title, 100, 0, 0);
            }
            finishBatchDone(succeeded, total, failed);
        } catch (DownloadCancelled e) {
            finishBatchCancelled(succeeded, total);
        } catch (DownloadException e) {
            // 整批掃描失敗（不是單項）：整批報錯，不吞。
            Listener l = listener;
            deleteQuietly(null);
            batchActive = false;
            running.set(false);
            stopForeground(true);
            stopSelf();
            if (l != null) {
                l.onError(e.getCode(), e.getMessage());
            }
        }
    }

    /** 單項：完整 resolve→政策選片→下載（＋音檔轉 mp3）→存檔→清暫存。 */
    private void downloadBatchItem(PlaylistItem item, boolean audioMode, int maxHeight, int index,
                                   int total)
            throws DownloadException {
        File staging = new File(getCacheDir(), "batch-" + System.currentTimeMillis() + "-" + index);
        try {
            File landed;
            if (audioMode) {
                landed = Downloader.download(this, item.url, staging,
                        "bestaudio/best", "audio",
                        (percent, eta, speed, line) -> {
                            if (cancelFlag.get()) {
                                throw new DownloadCancelled();
                            }
                            emitBatch(index, total, item.title, percent, eta, speed);
                        }, cancelFlag);
                try {
                    landed = transcoder.transcode(landed);
                } catch (DownloadException e) {
                    if (e.getPartialFile() != null) {
                        landed = e.getPartialFile();
                    }
                } catch (Throwable t) {
                    android.util.Log.e("DownloadService", "整批轉檔失敗，留原檔", t);
                }
            } else {
                ResolveResult full;
                try {
                    full = ResolveEngine.resolve(this, item.url, true);
                } catch (ResolveException e) {
                    throw new DownloadException(e.getCode(), e.getMessage(), e);
                }
                VideoFormat picked =
                        Downloader.pickByPolicy(full.videoOptions(), maxHeight);
                if (picked == null) {
                    throw new DownloadException(DownloadError.EXTRACT, "沒有可用畫質");
                }
                landed = Downloader.download(this, item.url, staging, picked.formatId, "video",
                        (percent, eta, speed, line) -> {
                            if (cancelFlag.get()) {
                                throw new DownloadCancelled();
                            }
                            emitBatch(index, total, item.title, percent, eta, speed);
                        }, cancelFlag);
            }
            MediaStoreSaver.save(this, landed);
        } finally {
            deleteQuietly(staging);
        }
    }

    private void emitBatch(int index, int total, String title,
                           float percent, long eta, long speed) {
        updateNotification((int) percent, eta, speed);
        Listener l = listener;
        if (l != null) {
            l.onBatchProgress(index, total, title, percent, eta, speed);
        }
    }

    private void finishBatchDone(int succeeded, int total, List<BatchFailure> failed) {
        batchActive = false;
        running.set(false);
        stopForeground(true);
        stopSelf();
        Listener l = listener;
        if (l != null) {
            l.onBatchDone(succeeded, total, failed);
        }
    }

    private void finishBatchCancelled(int succeeded, int total) {
        batchActive = false;
        running.set(false);
        stopForeground(true);
        stopSelf();
        Listener l = listener;
        if (l != null) {
            l.onError(DownloadError.CANCELLED, "已完成 " + succeeded + "/" + total + " 項");
        }
    }

    /** 逐項覆寫表 {"videoId": maxHeight}；壞掉就當沒有（不讓整批死）。 */
    static Map<String, Integer> parseOverrides(String json) {
        Map<String, Integer> out = new HashMap<>();
        if (json == null || json.isEmpty()) {
            return out;
        }
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                int h = o.optInt(k, -1);
                if (h > 0) {
                    out.put(k, h);
                }
            }
        } catch (org.json.JSONException e) {
            // 壞掉就當沒有（不讓整批死；JVM 單測不能碰 android.util.Log，不記 log）。
            return out;
        }
        return out;
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
