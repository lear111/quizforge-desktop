package io.quizforge.infrastructure.extension;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Host-owned approvals, bound to an immutable package hash as well as its ID/version. */
public final class ExtensionPermissionStore {
    private final Path root, file;
    private final ObjectMapper json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record Data(int schemaVersion, Map<String,Map<String,Set<String>>> packages) { }
    public ExtensionPermissionStore(Path root) { this.root=root.toAbsolutePath().normalize();file=this.root.resolve(".permissions.json"); }
    private String key(ExtensionPackageStore.InstalledExtension extension) {
        return extension.manifest().id()+"@"+extension.manifest().version()+"#"+extension.sha256();
    }
    private Data read() throws IOException {
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return new Data(1,Map.of());
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>1024*1024)throw new IOException("拓展权限记录无效");
        Data data=json.readValue(Files.readString(file),Data.class);
        if(data==null||data.schemaVersion()!=1||data.packages()==null||data.packages().values().stream().anyMatch(java.util.Objects::isNull))throw new IOException("拓展权限记录格式无效");
        return data;
    }
    public boolean hasApproval(ExtensionPackageStore.InstalledExtension extension) throws IOException { return read().packages().containsKey(key(extension)); }
    public Map<String,Set<String>> grants(ExtensionPackageStore.InstalledExtension extension) throws IOException {
        var saved=read().packages().getOrDefault(key(extension),Map.of());
        var result=new LinkedHashMap<String,Set<String>>();
        for(var type:extension.manifest().types()) {
            var approved=ExtensionPermissions.validate(saved.get(type.id()));
            result.put(type.id(),approved.stream().filter(type.permissions()::contains).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
        return Map.copyOf(result);
    }
    public void approve(ExtensionPackageStore.InstalledExtension extension,Map<String,Set<String>> approved) throws IOException {
        var requested=new LinkedHashMap<String,Set<String>>();extension.manifest().types().forEach(type->requested.put(type.id(),type.permissions()));
        var checked=new LinkedHashMap<String,Set<String>>();
        for(var entry:approved.entrySet()) {
            var values=ExtensionPermissions.validate(entry.getValue());
            if(!requested.containsKey(entry.getKey())||!requested.get(entry.getKey()).containsAll(values))throw new IllegalArgumentException("不能授予未声明的拓展权限");
            checked.put(entry.getKey(),values);
        }
        var packages=new LinkedHashMap<>(read().packages());packages.put(key(extension),Map.copyOf(checked));
        String encoded=json.writeValueAsString(new Data(1,packages));if(encoded.length()>1024*1024)throw new IOException("拓展权限记录过大");
        Files.createDirectories(root);Path temporary=Files.createTempFile(root,".permissions-",".tmp");
        try{Files.writeString(temporary,encoded);Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temporary);}
    }
}
