package io.quizforge.desktop.ui;

import java.awt.AlphaComposite;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javax.imageio.ImageIO;

/** Encodes real clipboard pixels, including Windows DIBs with unusable FX alpha. */
final class CanvasClipboardImage {
    private static final long MAX_PIXELS = 25_000_000;

    static Map<String,Object> read(Image image) {
        return encode(image,CanvasClipboardImage::readAwtImage);
    }

    static Map<String,Object> encode(Image image,Supplier<java.awt.Image> fallback) {
        if(image==null || image.getPixelReader()==null)
            throw new IllegalArgumentException("剪贴板图片无法读取");
        int width=(int)image.getWidth(),height=(int)image.getHeight();
        checkSize(width,height);
        int[] pixels=new int[width*height];
        image.getPixelReader().getPixels(0,0,width,height,PixelFormat.getIntArgbInstance(),pixels,0,width);
        var picture=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        picture.setRGB(0,0,width,height,pixels,0,width);
        if(!hasVisiblePixels(pixels)) {
            // Glass may treat the reserved alpha byte of a Windows DIB as real
            // alpha. Use AWT's decoder of the same clipboard, not guessed pixels.
            var source=fallback.get();
            if(source==null || source.getWidth(null)!=width || source.getHeight(null)!=height)
                throw new IllegalArgumentException("剪贴板图片读取失败，请重新复制原图");
            var graphics=picture.createGraphics();
            try { graphics.setComposite(AlphaComposite.Src);graphics.drawImage(source,0,0,null); }
            finally { graphics.dispose(); }
            if(!hasVisiblePixels(picture.getRGB(0,0,width,height,null,0,width)))
                throw new IllegalArgumentException("剪贴板读出的图片完全透明，请重新复制原图或使用插入图片");
        }
        try(var bytes=new ByteArrayOutputStream()) {
            if(!ImageIO.write(picture,"png",bytes))throw new IOException("PNG encoder unavailable");
            return Map.of("value","data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray()),
                    "width",width,"height",height);
        }catch(IOException failure){throw new IllegalArgumentException("无法读取剪贴板图片",failure);}
    }

    private static boolean hasVisiblePixels(int[] pixels) {
        for(int pixel:pixels)if((pixel>>>24)!=0)return true;
        return false;
    }
    private static void checkSize(int width,int height) {
        if(width<=0 || height<=0 || (long)width*height>MAX_PIXELS)
            throw new IllegalArgumentException("剪贴板图片无法读取或像素过大");
    }
    private static java.awt.Image readAwtImage() {
        try {
            var clipboard=java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            if(clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor))
                return (java.awt.Image)clipboard.getData(DataFlavor.imageFlavor);
            return null;
        }catch(Exception failure){throw new IllegalArgumentException("无法读取原始剪贴板图片，请重新复制",failure);}
    }
}
