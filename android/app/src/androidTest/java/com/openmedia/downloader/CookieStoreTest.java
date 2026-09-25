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
}
