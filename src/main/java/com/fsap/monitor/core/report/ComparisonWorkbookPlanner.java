package com.fsap.monitor.core.report;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fsap.monitor.core.report.ReportGenerationService.QueryTable;
import com.fsap.monitor.infra.config.FsapProperties;

/**
 * 依設定決定跨分類比較活頁簿要放哪些分頁、什麼名稱與順序（只規劃，不寫檔）。
 */
final class ComparisonWorkbookPlanner {

    static final String BZKIND_PLACEHOLDER = "{pr_bzkind}";

    private ComparisonWorkbookPlanner() {
    }

    record SheetPlan(String sheetName, QueryTable table) { }

    record Plan(List<SheetPlan> sheets, List<String> messages) { }

    /**
     * @param tablesByPass 分類（ALL 或分類代碼）→ 報表名稱（不含 .csv）→ 該輪已算好的結果
     * @param executedBzkinds 本次實際產出的分類（不含 ALL），config 沒指定 bzkinds 時依此順序
     */
    static Plan plan(
            FsapProperties.CombinedWorkbook config,
            Map<String, Map<String, QueryTable>> tablesByPass,
            List<String> executedBzkinds
    ) {
        List<SheetPlan> sheets = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();

        for (FsapProperties.SheetSpec spec : config.getAllSheets()) {
            addSheet(sheets, messages, usedNames, tablesByPass, spec.getSheetName(), BzkindSelection.ALL, spec.getDataSource());
        }
        List<String> bzkinds = config.getBzkinds().isEmpty() ? executedBzkinds : config.getBzkinds();
        for (String bzkind : bzkinds) {
            if (!tablesByPass.containsKey(bzkind)) {
                messages.add("[比較活頁簿] 分類 " + bzkind + " 本次沒有產出，略過");
                continue;
            }
            for (FsapProperties.SheetSpec spec : config.getReports()) {
                String sheetName = spec.getSheetName().replace(BZKIND_PLACEHOLDER, bzkind);
                addSheet(sheets, messages, usedNames, tablesByPass, sheetName, bzkind, spec.getDataSource());
            }
        }
        return new Plan(sheets, messages);
    }

    static String reportKey(String dataSource) {
        String name = dataSource == null ? "" : dataSource.trim();
        return name.toLowerCase().endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
    }

    private static void addSheet(
            List<SheetPlan> sheets,
            List<String> messages,
            Set<String> usedNames,
            Map<String, Map<String, QueryTable>> tablesByPass,
            String sheetName,
            String pass,
            String dataSource
    ) {
        QueryTable table = tablesByPass.getOrDefault(pass, Map.of()).get(reportKey(dataSource));
        if (table == null) {
            messages.add("[比較活頁簿] 找不到 " + pass + "/" + dataSource + "，略過分頁「" + sheetName + "」");
            return;
        }
        String finalName = ReportGenerationService.nextSheetName(sheetName, usedNames);
        if (!finalName.equals(sheetName)) {
            messages.add("[比較活頁簿] 分頁名稱「" + sheetName + "」調整為「" + finalName + "」（Excel 限制：不可含特殊字元、最長 31 字、不可重複）");
        }
        sheets.add(new SheetPlan(finalName, table));
    }
}
