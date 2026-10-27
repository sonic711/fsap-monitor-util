package com.fsap.monitor.core.report;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * 驗證要額外產出的業務分類。ALL 一律產出，不需要也不能再列。
 */
final class BzkindSelection {

    static final String ALL = "ALL";
    static final String UNCLASSIFIED = "其他/待分類";

    private BzkindSelection() {
    }

    /**
     * 回傳實際要產出的分類（已排序、去重）。不存在於最新 PR_CFG 快照的分類
     * （含「其他/待分類」，它不是 PR_CFG 的分類）會跳過，原因寫入 messages，其他照常。
     */
    static List<String> select(List<String> requested, Collection<String> valid, List<String> messages) {
        TreeSet<String> accepted = new TreeSet<>();
        if (requested == null) {
            return List.of();
        }
        for (String raw : requested) {
            String bzkind = raw == null ? "" : raw.trim();
            if (bzkind.isEmpty() || accepted.contains(bzkind)) {
                continue;
            }
            if (ALL.equalsIgnoreCase(bzkind)) {
                messages.add("[分類驗證] 不需要列 " + ALL + "（ALL 一律產出），已忽略");
                continue;
            }
            if (!valid.contains(bzkind)) {
                String hint = UNCLASSIFIED.equals(bzkind) ? "（它不是 PR_CFG 的分類，不能指定）" : "";
                messages.add("[分類驗證] 分類 " + bzkind + " 不在 PR_CFG 的分類清單裡" + hint + "，已跳過");
                continue;
            }
            if (!isSafeDirectoryName(bzkind)) {
                messages.add("[分類驗證] 分類 " + bzkind + " 含有不能用於目錄名稱的字元，已跳過");
                continue;
            }
            accepted.add(bzkind);
        }
        return List.copyOf(accepted);
    }

    private static boolean isSafeDirectoryName(String name) {
        return !name.equals(".") && !name.equals("..") && name.chars().noneMatch(c -> "/\\:*?\"<>|".indexOf(c) >= 0);
    }
}
