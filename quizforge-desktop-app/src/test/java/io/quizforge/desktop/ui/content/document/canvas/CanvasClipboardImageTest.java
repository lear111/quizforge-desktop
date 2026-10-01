package io.quizforge.desktop.ui.content.document.canvas;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanvasClipboardImageTest {
    @Test void windowsTransparentFxBitmapUsesOriginalAwtPixels() throws Exception {
        var fx=new WritableImage(3,2);
        var original=new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB);
        original.setRGB(0,0,0xff2457ab);original.setRGB(1,0,0xffbb8833);
        var result=decode(CanvasClipboardImage.encode(fx,()->original));
        assertEquals(3,result.getWidth());assertEquals(2,result.getHeight());
        assertEquals(0xff2457ab,result.getRGB(0,0));assertEquals(0xffbb8833,result.getRGB(1,0));
    }

    @Test void validAlphaIsPreservedWithoutReadingAnotherClipboardFormat() throws Exception {
        var fx=new WritableImage(2,1);
        fx.getPixelWriter().setArgb(0,0,0x800000ff);
        fx.getPixelWriter().setArgb(1,0,0x00000000);
        var result=decode(CanvasClipboardImage.encode(fx,()->{throw new AssertionError("Unexpected fallback");}));
        assertEquals(0x800000ff,result.getRGB(0,0));assertEquals(0,result.getRGB(1,0));
    }

    @Test void unreadableTransparentClipboardIsRejectedInsteadOfInsertingBlankImage() {
        var fx=new WritableImage(2,1);
        assertThrows(IllegalArgumentException.class,()->CanvasClipboardImage.encode(fx,()->null));
        assertThrows(IllegalArgumentException.class,()->CanvasClipboardImage.encode(fx,()->new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB)));
    }

    private static BufferedImage decode(Map<String,Object> data) throws Exception {
        String value=(String)data.get("value");
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(value.substring(value.indexOf(',')+1))));
    }
}
