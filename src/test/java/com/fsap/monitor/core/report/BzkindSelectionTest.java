package com.fsap.monitor.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class BzkindSelectionTest {

    private static final List<String> VALID = List.of("NCL", "NIS", "FSAP");

    @Test
    void acceptsValidKindsSortedAndDistinct() {
        List<String> messages = new ArrayList<>();
        List<String> selected = BzkindSelection.select(List.of("NIS", "NCL", "NIS"), VALID, messages);
        assertThat(selected).containsExactly("NCL", "NIS");
        assertThat(messages).isEmpty();
    }

    @Test
    void ignoresAllInAnyCase() {
        List<String> messages = new ArrayList<>();
        List<String> selected = BzkindSelection.select(List.of("all", "ALL", "NCL"), VALID, messages);
        assertThat(selected).containsExactly("NCL");
        assertThat(messages).hasSize(2);
    }

    @Test
    void skipsUnknownKindButKeepsOthers() {
        List<String> messages = new ArrayList<>();
        List<String> selected = BzkindSelection.select(List.of("NCL", "XYZ"), VALID, messages);
        assertThat(selected).containsExactly("NCL");
        assertThat(messages).singleElement().asString().contains("XYZ");
    }

    @Test
    void rejectsUnclassifiedWithHint() {
        List<String> messages = new ArrayList<>();
        List<String> selected = BzkindSelection.select(List.of(BzkindSelection.UNCLASSIFIED), VALID, messages);
        assertThat(selected).isEmpty();
        assertThat(messages).singleElement().asString().contains("不能指定");
    }

    @Test
    void rejectsKindsThatCannotBeDirectoryNames() {
        List<String> valid = List.of("A/B", "..", "OK");
        List<String> messages = new ArrayList<>();
        List<String> selected = BzkindSelection.select(Arrays.asList("A/B", "..", "OK"), valid, messages);
        assertThat(selected).containsExactly("OK");
        assertThat(messages).hasSize(2);
    }

    @Test
    void handlesNullAndBlankInput() {
        List<String> messages = new ArrayList<>();
        assertThat(BzkindSelection.select(null, VALID, messages)).isEmpty();
        assertThat(BzkindSelection.select(Arrays.asList(null, " ", ""), VALID, messages)).isEmpty();
        assertThat(messages).isEmpty();
    }
}
