package io.github.lucf15.tiffrenderer;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class JavaUsageTest {
    @Test public void blockingAndFutureApisRenderWithoutCoroutineBoilerplate() throws Exception {
        var path = Files.createTempFile("tiff-java-api-", ".tif");
        try {
            try (var fixture = getClass().getResourceAsStream("/single_page_rgb.tif")) {
                Files.write(path, fixture.readAllBytes());
            }
            try (var document = BlockingTiffRenderer.open(path)) {
                assertEquals(1, document.pageCount());
                var image = document.renderImage(0, 16, 16);
                assertEquals(16, image.getWidth());
                assertEquals(12, image.getHeight());
            }
            var result = TiffFutures.render(path, 0, RenderSize.fit(16, 16)).get(10, TimeUnit.SECONDS);
            assertEquals(16, result.getImage().getWidth());
            assertEquals(2, TiffBitmap.create(2, 2).getWidth());
        } finally { Files.deleteIfExists(path); }
    }
}
