package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * CookieStore 存取 round-trip（ticket cookie-login/01）：
 * 加密存取在真機/模擬器上通才算數，JVM 跑不了 KeyStore。
 */
@RunWith(AndroidJUnit4.class)
public class CookieStoreTest {
    private static final String FAKE =
            "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t1\tSID\tABC123\n";

    @Test
    public void saveLoadClearRoundTrip() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        CookieStore store = new CookieStore(ctx);
        store.clear(CookieStore.YOUTUBE);
        assertFalse(store.has(CookieStore.YOUTUBE));
        assertNull(store.load(CookieStore.YOUTUBE));
        store.save(CookieStore.YOUTUBE, FAKE);
        assertTrue(store.has(CookieStore.YOUTUBE));
        assertEquals(FAKE, store.load(CookieStore.YOUTUBE));
        assertTrue(store.isEnabled(CookieStore.YOUTUBE));
        store.setEnabled(CookieStore.YOUTUBE, false);
        assertFalse(store.isEnabled(CookieStore.YOUTUBE));
        store.setEnabled(CookieStore.YOUTUBE, true);
        store.clear(CookieStore.YOUTUBE);
        assertFalse(store.has(CookieStore.YOUTUBE));
    }

    @Test
    public void sitesAreIndependent() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        CookieStore store = new CookieStore(ctx);
        store.clear(CookieStore.BILIBILI);
        store.clear(CookieStore.TWITTER);
        store.save(CookieStore.BILIBILI, FAKE);
        assertTrue(store.has(CookieStore.BILIBILI));
        assertFalse(store.has(CookieStore.TWITTER));
        store.clear(CookieStore.BILIBILI);
    }

    @Test
    public void resolveWithCookieStillWorks() throws Exception {
        // 存了假 cookie 後 resolve 公開片：爛 cookie 必須被忽略，不能炸正常路。
        // （真登入牆素材由擁有者手動驗；這裡只證明注入管線不斷。）
        Context ctx = ApplicationProvider.getApplicationContext();
        CookieStore store = new CookieStore(ctx);
        store.clear(CookieStore.YOUTUBE);
        store.save(CookieStore.YOUTUBE, FAKE);
        try {
            ResolveResult r = ResolveEngine.resolve(ctx,
                    "https://www.youtube.com/watch?v=aqz-KE-bpKQ", true);
            assertTrue(r.hasPlayableVideo());
        } finally {
            store.clear(CookieStore.YOUTUBE);
        }
    }

    @Test
    public void prepareWritesTempFileHonoringSwitch() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        CookieStore store = new CookieStore(ctx);
        store.clear(CookieStore.YOUTUBE);
        // 沒存：null（不是錯誤）
        assertNull(CookieFiles.prepare(ctx, CookieStore.YOUTUBE));
        // 存了但關掉：null
        store.save(CookieStore.YOUTUBE, FAKE);
        store.setEnabled(CookieStore.YOUTUBE, false);
        assertNull(CookieFiles.prepare(ctx, CookieStore.YOUTUBE));
        // 存了且開著：暫存檔內容一致，用完即刪
        store.setEnabled(CookieStore.YOUTUBE, true);
        java.io.File f = CookieFiles.prepare(ctx, CookieStore.YOUTUBE);
        assertTrue(f != null && f.isFile());
        byte[] buf = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int n = 0, r;
            while ((r = in.read(buf, n, buf.length - n)) > 0) {
                n += r;
            }
        }
        assertEquals(FAKE, new String(buf, java.nio.charset.StandardCharsets.UTF_8));
        CookieFiles.dispose(f);
        assertFalse(f.exists());
        // 未知站：null
        assertNull(CookieFiles.prepare(ctx, "netflix"));
        store.clear(CookieStore.YOUTUBE);
    }
}
