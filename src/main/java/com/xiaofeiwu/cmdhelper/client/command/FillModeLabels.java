package com.xiaofeiwu.cmdhelper.client.command;

import java.util.Map;

/**
 * The /fill and /setblock mode keywords (replace/keep/destroy/hollow/outline) are read live
 * from the server's command tree, not hand-typed — but the raw English keyword means nothing
 * to a player who doesn't already know the command. This only translates the label shown in
 * the dropdown; the keyword sent to the server is untouched.
 */
public final class FillModeLabels {

    private static final Map<String, String> LABELS = Map.of(
            "(默认)", "默认（=替换）",
            "replace", "替换（全部换新）",
            "destroy", "摧毁（先打碎再放）",
            "keep", "保留（只填空气处）",
            "hollow", "掏空（内部清空）",
            "outline", "描边（只填最外层）"
    );

    private FillModeLabels() {
    }

    public static String labelFor(String keyword) {
        return LABELS.getOrDefault(keyword, keyword);
    }
}
