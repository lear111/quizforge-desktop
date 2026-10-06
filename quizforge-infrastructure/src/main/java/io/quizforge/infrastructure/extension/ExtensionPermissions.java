package io.quizforge.infrastructure.extension;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Known host operations; declarations never grant native access by themselves. */
public final class ExtensionPermissions {
    private ExtensionPermissions() { }
    public static final Map<String,String> LABELS = Map.ofEntries(
        Map.entry("question.edit","编辑当前题目及富文本"), Map.entry("bank.save","保存当前题库"),
        Map.entry("bank.add","新增题卡"), Map.entry("bank.duplicate","复制题卡"), Map.entry("bank.delete","删除题卡"),
        Map.entry("answer.write","填写当前答案"), Map.entry("practice.submit","提交当前答案"), Map.entry("practice.retry","重试当前题目"),
        Map.entry("navigation","切换题卡"), Map.entry("sources.open","打开当前题目的引用来源"), Map.entry("sources.manage","添加或移除引用来源"),
        Map.entry("learning.mode","切换练习与草稿模式"), Map.entry("whiteboard.tools","切换白板工具"),
        Map.entry("whiteboard.history","撤销或重做白板操作"), Map.entry("whiteboard.clear","清空当前白板"),
        Map.entry("whiteboard.appearance","修改白板底色和样式"), Map.entry("whiteboard.zoom","调整白板缩放")
    );
    public static Set<String> validate(Collection<String> permissions) {
        if (permissions == null) return Set.of();
        if (permissions.stream().anyMatch(value -> value==null || !LABELS.containsKey(value))) throw new IllegalArgumentException("未知的题型拓展权限");
        return Set.copyOf(permissions);
    }
}
