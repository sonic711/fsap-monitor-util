package com.fsap.monitor.web.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fsap.monitor.core.query.QueryService;
import com.fsap.monitor.core.query.SchemaBrowseService;
import com.fsap.monitor.core.query.QueryHistoryService;
import com.fsap.monitor.web.dto.SqlQueryRequest;
import com.fsap.monitor.web.dto.SqlQueryResponse;

@Validated
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
/**
 * 提供 ad-hoc SQL 執行與 schema 檢視的 REST 端點。
 */
public class QueryController {

    private final QueryService queryService;
    private final SchemaBrowseService schemaBrowseService;
    private final QueryHistoryService queryHistoryService;

    public QueryController(QueryService queryService, SchemaBrowseService schemaBrowseService, QueryHistoryService queryHistoryService) {
        this.queryService = queryService;
        this.schemaBrowseService = schemaBrowseService;
        this.queryHistoryService = queryHistoryService;
    }

    @PostMapping("/query")
    public SqlQueryResponse execute(@RequestBody @Valid SqlQueryRequest request) {
        try {
            var result = queryService.execute(request.sql());
            queryHistoryService.record(request.sql(), result.rows().size());
            return new SqlQueryResponse("success", result.columns(), result.rows(), result.rows(), result.rows().size());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/schema")
    public Map<String, List<Map<String, Object>>> schema() {
        try {
            return Map.of("tables", schemaBrowseService.loadSchemaSnapshot());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), exception);
        }
    }
}
