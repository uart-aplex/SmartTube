package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;

import androidx.core.content.FileProvider;

import com.liskovsoft.smartyoutubetv2.common.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateFileProviderTest {
    @Test
    public void providerStartsWithoutExternalStorageAndSharesInternalUpdate() {
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override
            public File[] getExternalCacheDirs() {
                throw new AssertionError("Startup must not query external cache");
            }

            @Override
            public File[] getExternalMediaDirs() {
                throw new AssertionError("Startup must not query external media");
            }
        };
        ProviderInfo info = new ProviderInfo();
        info.packageName = context.getPackageName();
        info.name = FileProvider.class.getName();
        info.authority = context.getPackageName() + ".startup_test_provider";
        info.exported = false;
        info.grantUriPermissions = true;
        info.metaData = new Bundle();
        info.metaData.putInt("android.support.FILE_PROVIDER_PATHS", R.xml.provider_paths);
        shadowOf(context.getPackageManager()).addOrUpdateProvider(info);

        FileProvider provider = new FileProvider();
        provider.attachInfo(context, info);
        Uri uri = FileProvider.getUriForFile(context, info.authority,
                new File(context.getCacheDir(), "update.apk"));

        assertEquals("content", uri.getScheme());
        assertEquals(info.authority, uri.getAuthority());
        assertEquals("/cache_files/update.apk", uri.getPath());
    }
}
