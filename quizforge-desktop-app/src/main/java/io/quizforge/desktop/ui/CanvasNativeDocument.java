package io.quizforge.desktop.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Native Canvas serialization, with no conversion of text, layout or image dimensions. */
final class CanvasNativeDocument {
    static final String MEDIA_TYPE="application/vnd.quizforge.canvas+json";
    static final int MAX_BYTES=64*1024*1024;
    private static final ObjectMapper JSON=new ObjectMapper();
    private CanvasNativeDocument() { }
    static byte[] bytes(String source) {
        byte[] bytes=source.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("富文本文档不能超过 64 MiB");
        try {
            var root=JSON.readTree(source);
            if(root==null || !root.isObject() || !root.path("version").isTextual()
                    || !root.path("data").path("main").isArray() || !root.path("options").isObject())
                throw new IllegalArgumentException("无效的 Canvas 原生文档");
        }catch(IOException failure){throw new IllegalArgumentException("无效的 Canvas 原生文档",failure);}
        return bytes;
    }
    static String read(InputStream stream) throws IOException {
        byte[] bytes=stream.readNBytes(MAX_BYTES+1);
        if(bytes.length>MAX_BYTES)throw new IOException("富文本文档过大");
        String source=new String(bytes,StandardCharsets.UTF_8);bytes(source);return source;
    }
    static String hash(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
