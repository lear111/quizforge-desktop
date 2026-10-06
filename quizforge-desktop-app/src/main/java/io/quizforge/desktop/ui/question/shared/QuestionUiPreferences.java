package io.quizforge.desktop.ui.question.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Presentation preferences never grant platform or data mutation permissions. */
public final class QuestionUiPreferences {
    private static final Set<String> EDITOR=Set.of("title","save","position","typeLabel","add","duplicate","delete","sources","outline","errors");
    private static final Set<String> PRACTICE=Set.of("card","typeLabel","position","score","state","submit","retry","confirmation","note","sources","outline","draftToggle","draftToolbar","draftZoom","errors");
    private QuestionUiPreferences() { }
    public static Map<String,Boolean> validate(Map<String,Object> value,boolean editor) {
        var allowed=editor?EDITOR:PRACTICE;var result=new LinkedHashMap<String,Boolean>();
        value.forEach((name,option)->{
            if(!allowed.contains(name)||!(option instanceof Boolean visible))throw new IllegalArgumentException("无效公共 UI 配置："+name);
            result.put(name,visible);
        });
        return Map.copyOf(result);
    }
    public static boolean visible(Map<String,Boolean> preferences,String name) { return preferences.getOrDefault(name,true); }
}
