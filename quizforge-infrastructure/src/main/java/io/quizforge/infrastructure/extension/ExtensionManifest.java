package io.quizforge.infrastructure.extension;

import java.util.List;
import java.util.Set;

/** Portable .qfext metadata. Asset paths are always relative to the package root. */
public record ExtensionManifest(int packageFormatVersion, String id, String name, String version,
        int sdkApiMajor, Integer minSdkApiMinor, List<Type> types) {
    public ExtensionManifest { minSdkApiMinor=minSdkApiMinor==null?0:minSdkApiMinor;types = List.copyOf(types); }
    public record Type(String id, String label, int dataVersion, String family,
            Set<String> capabilities, Set<String> permissions, String questionSchema, String answerSchema,
            String rules, String editor, String renderer, String defaultQuestion,
            String editorScript, String rendererScript,
            @com.fasterxml.jackson.annotation.JsonFormat(with = com.fasterxml.jackson.annotation.JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> styles) {
        public Type { capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
            permissions = ExtensionPermissions.validate(permissions);
            styles = styles == null ? List.of() : List.copyOf(styles); }
    }
}
