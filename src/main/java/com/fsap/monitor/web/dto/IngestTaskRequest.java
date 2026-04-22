package com.fsap.monitor.web.dto;

public record IngestTaskRequest(boolean force, Integer limit, String date) { }
