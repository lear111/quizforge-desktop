package io.quizforge.infrastructure.filesystem.qbank;

import io.quizforge.core.question.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Local PNG/JPEG import; decoded and bounded before creating package metadata. */
public final class QBankImageImporter {
    public static final long MAX_BYTES=32L*1024*1024;
    public static final long MAX_PIXELS=25_000_000;
    public ImportedImage read(Path file) {
        try {
            byte[] bytes;
            try(var input=Files.newInputStream(file)) {
                bytes=input.readNBytes((int)MAX_BYTES+1);
            }
            if(bytes.length>MAX_BYTES) throw new IllegalArgumentException("图片不能超过 32 MiB");
            boolean png=bytes.length>=8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
            boolean jpeg=bytes.length>=3 && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255;
            if(!png && !jpeg) throw new IllegalArgumentException("仅支持有效的 PNG / JPEG 图片");
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw new IllegalArgumentException("无法解码图片");
                var reader=readers.next();
                try {
                    reader.setInput(input,true,true);
                    if((long)reader.getWidth(0)*reader.getHeight(0)>MAX_PIXELS) throw new IllegalArgumentException("图片像素过大");
                    if(reader.read(0)==null) throw new IllegalArgumentException("无法解码图片");
                } finally { reader.dispose(); }
            }
            String id="res_"+UUID.randomUUID();
            String hash=HexFormat.of().formatHex(QBankPackageReader.sha256().digest(bytes));
            return new ImportedImage(new QBankResource(id,ResourceKind.IMAGE,png?"image/png":"image/jpeg",
                    "resources/"+id+(png?".png":".jpg"),hash),bytes);
        } catch(IOException error) { throw new IllegalArgumentException("无法读取或解码图片",error); }
    }
    public record ImportedImage(QBankResource resource, byte[] bytes) {
        public ImportedImage { bytes=bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        public InputStream open() { return new ByteArrayInputStream(bytes); }
    }
}
