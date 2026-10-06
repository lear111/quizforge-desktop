package io.quizforge.infrastructure.extension;

import java.io.IOException;

/** SDK minor versions add capabilities; incompatible major/package formats never execute. */
public final class ExtensionCompatibility {
    private ExtensionCompatibility() { }
    public static final int PACKAGE_FORMAT=2, SDK_MAJOR=2, SDK_MINOR=1;
    public static void requireSupported(ExtensionManifest manifest) throws IOException {
        if(manifest.packageFormatVersion()!=PACKAGE_FORMAT)
            throw new IOException("扩展需要格式 "+manifest.packageFormatVersion()+"，当前支持格式 "+PACKAGE_FORMAT+
                    (manifest.packageFormatVersion()>PACKAGE_FORMAT?"；请更新应用":"；请更新扩展为 HTML/JSON 格式 2"));
        if(manifest.sdkApiMajor()!=SDK_MAJOR)
            throw new IOException("扩展需要 SDK "+manifest.sdkApiMajor()+"，当前 SDK "+SDK_MAJOR+"."+SDK_MINOR+
                    (manifest.sdkApiMajor()>SDK_MAJOR?"；请更新应用":"；旧 SDK 已不再支持，请更新扩展"));
        int minor=manifest.minSdkApiMinor();
        if(minor<0)throw new IOException("minSdkApiMinor 必须是非负整数");
        if(minor>SDK_MINOR)throw new IOException("扩展需要 SDK "+SDK_MAJOR+"."+minor+"，当前 SDK "+SDK_MAJOR+"."+SDK_MINOR+"；请更新应用");
    }
}
