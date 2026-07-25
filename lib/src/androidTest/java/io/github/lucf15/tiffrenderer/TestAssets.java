/*
 * Copyright 2026 lucf15
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lucf15.tiffrenderer;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Copies a {@code src/androidTest/assets/} fixture into the app's cache dir and opens it as a
 * {@link ParcelFileDescriptor} -- {@link TiffRenderer} needs a real, seekable fd, which an
 * {@code AssetManager} stream alone can't provide.
 */
final class TestAssets {

    private TestAssets() {}

    static ParcelFileDescriptor open(String assetName) throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        File copy = new File(context.getCacheDir(), assetName);
        try (InputStream in = context.getAssets().open(assetName);
                FileOutputStream out = new FileOutputStream(copy)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
        return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
