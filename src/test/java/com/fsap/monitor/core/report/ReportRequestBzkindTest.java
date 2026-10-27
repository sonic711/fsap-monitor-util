package com.fsap.monitor.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fsap.monitor.infra.config.FsapProperties;

class ReportRequestBzkindTest {

    private static ReportGenerationRequest request(List<String> bzkinds) {
        return new ReportGenerationRequest(null, false, "2026-09", null, null, null, null, null, null, bzkinds);
    }

    @Test
    void normalizeTrimsDropsBlanksAndDeduplicatesInOrder() {
        var normalized = request(Arrays.asList(" NIS ", "", null, "NCL", "NIS")).normalize();
        assertThat(normalized.targetBzkinds()).containsExactly("NIS", "NCL");
    }

    @Test
    void normalizeKeepsUnspecifiedAsNullAndExplicitEmptyAsEmpty() {
        assertThat(request(null).normalize().targetBzkinds()).isNull();
        assertThat(request(List.of()).normalize().targetBzkinds()).isEmpty();
    }

    @Test
    void parseBzkindsDistinguishesUnspecifiedFromEmpty() {
        assertThat(ReportGenerationRequest.parseBzkinds(null)).isNull();
        assertThat(ReportGenerationRequest.parseBzkinds("")).isEmpty();
        assertThat(ReportGenerationRequest.parseBzkinds(" NCL, NIS，FSAP ,")).containsExactly("NCL", "NIS", "FSAP");
    }

    @Test
    void templateParametersCarryTheKindOfCurrentPass() {
        var request = request(null);
        assertThat(request.templateParameters().get("targetBzkind")).isEqualTo("ALL");
        assertThat(request.templateParameters("NCL").get("targetBzkind")).isEqualTo("NCL");
        assertThat(request.templateParameters("NCL").get("targetMonth")).isEqualTo("2026-09");
    }

    @Test
    void summaryListsTargetKindsOnlyWhenPresent() {
        assertThat(request(null).summaryParts()).noneMatch(part -> part.startsWith("targetBzkinds"));
        assertThat(request(List.of("NCL", "NIS")).summaryParts()).contains("targetBzkinds=NCL,NIS");
    }

    @Test
    void defaultsServiceFallsBackToConfiguredKindsOnlyWhenUnspecified() {
        FsapProperties properties = new FsapProperties();
        properties.getReport().setTargetBzkinds(List.of("NCL", "NIS"));
        var service = new ReportParameterDefaultsService(properties);

        assertThat(service.resolve(request(null)).targetBzkinds()).containsExactly("NCL", "NIS");
        assertThat(service.resolve(request(List.of())).targetBzkinds()).isEmpty();
        assertThat(service.resolve(request(List.of("FSAP"))).targetBzkinds()).containsExactly("FSAP");
    }
}
