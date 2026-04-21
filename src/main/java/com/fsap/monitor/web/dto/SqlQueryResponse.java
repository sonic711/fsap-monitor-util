package com.fsap.monitor.web.dto;

import java.util.List;
import java.util.Map;

public record SqlQueryResponse(
        String status,
        List<String> columns,
        List<Map<String, Object>> rows,
        List<Map<String, Object>> data,
        int count
) { }
