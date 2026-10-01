package io.quizforge.desktop.ui.content.document.canvas;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Native Canvas serialization, with no conversion of text, layout or image dimensions. */
final class CanvasNativeDocument {
    static final String MEDIA_TYPE="application/vnd.quizforge.canvas+json";
    static final int MAX_BYTES=64*1024*1024;
    static final ObjectMapper JSON=new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxStringLength(MAX_BYTES).build()).build())
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private CanvasNativeDocument() { }
    static byte[] bytes(String source) {
        byte[] bytes=source.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("富文本文档不能超过 64 MiB");
        try {
            var root=JSON.readTree(source);
            if(root==null || !root.isObject() || !root.path("version").isTextual()
                    || !root.path("data").path("main").isArray() || !root.path("options").isObject())
                throw new IllegalArgumentException("无效的 Canvas 原生文档");
            validateImages(root);
        }catch(IOException failure){throw new IllegalArgumentException("无效的 Canvas 原生文档",failure);}
        return bytes;
    }
    private static void validateImages(com.fasterxml.jackson.databind.JsonNode node) {
        if(node.isObject() && "image".equalsIgnoreCase(node.path("type").asText())) {
            String value=node.path("value").asText();
            if(!value.matches("(?s)^data:image/(?:png|jpeg|gif|webp);base64,[A-Za-z0-9+/=\\r\\n]+$"))
                throw new IllegalArgumentException("文档图片必须使用题库内的图片数据，不能引用外部地址");
        }
        if(node.isContainerNode()) node.elements().forEachRemaining(CanvasNativeDocument::validateImages);
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
