package com.devdocs.ai.preretrieval.filter;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataFilterParserTest {

    @Test
    void nullOrBlankFiltersFallThroughToStandardFlow() {
        assertNull(MetadataFilterParser.parse(null, null));
        assertNull(MetadataFilterParser.parse(List.of(), null));
        assertNull(MetadataFilterParser.parse(List.of(" ", ""), ""));
    }

    @Test
    void parsesTypedAndCombinedConditions() {
        MetadataFilter filter = MetadataFilterParser.parse(List.of(
                "source_type:eq:web",
                "page:gte:3",
                "document_id:in:9, 10",
                "fetch_date:gte:2026-11-27T00:00:00Z",
                "source:prefix:https://docs.spring.io/"
        ), "or");

        assertEquals(MetadataFilter.Mode.OR, filter.mode());
        assertEquals(5, filter.conditions().size());
        assertEquals(List.of("WEB"), filter.conditions().get(0).values());
        assertEquals(List.of(3), filter.conditions().get(1).values());
        assertEquals(List.of(9L, 10L), filter.conditions().get(2).values());
        assertEquals(List.of(Timestamp.from(Instant.parse("2026-11-27T00:00:00Z"))),
                filter.conditions().get(3).values());
        assertEquals(List.of("https://docs.spring.io/"), filter.conditions().get(4).values());
        assertEquals("fetch_date gte 2026-11-27T00:00:00Z", filter.conditions().get(3).describe());
    }

    @Test
    void defaultsToAndAndIsCaseInsensitive() {
        MetadataFilter filter = MetadataFilterParser.parse(
                List.of("SOURCE_TYPE:EQ:STATIC", "Version:Eq:v1"), null);

        assertEquals(MetadataFilter.Mode.AND, filter.mode());
        assertEquals(FilterField.VERSION, filter.conditions().get(1).field());
    }

    @Test
    void valueMayContainColons() {
        MetadataFilter filter = MetadataFilterParser.parse(
                List.of("source:eq:https://example.com:8443/a"), null);

        assertEquals(List.of("https://example.com:8443/a"), filter.conditions().getFirst().values());
    }

    @Test
    void collectsAllErrorsForUnknownFieldsOperatorsAndValues() {
        InvalidFilterException exception = assertThrows(InvalidFilterException.class,
                () -> MetadataFilterParser.parse(List.of(
                        "content:eq:x",             // not whitelisted
                        "source_type:gt:WEB",       // operator not allowed for field
                        "page:eq:abc",              // wrong type
                        "page:eq:0",                // not positive
                        "source_type:eq:PDF",       // not an allowed enum value
                        "justgarbage",              // bad syntax
                        "fetch_date:gte:yesterday"  // not ISO-8601
                ), "XOR"));

        List<String> errors = exception.errors();
        assertEquals(8, errors.size(), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("filterMode must be AND or OR")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown field 'content'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("operator 'gt' is not allowed")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("expected <field>:<operator>:<value>")));
    }

    @Test
    void sqlInjectionAttemptsAreRejectedOrStayBoundValues() {
        assertThrows(InvalidFilterException.class, () -> MetadataFilterParser.parse(
                List.of("source_type;DROP TABLE chunks:eq:WEB"), null));
        assertThrows(InvalidFilterException.class, () -> MetadataFilterParser.parse(
                List.of("page:eq:1 OR 1=1"), null));

        MetadataFilter filter = MetadataFilterParser.parse(
                List.of("version:eq:1' OR '1'='1"), null);
        List<Object> parameters = new ArrayList<>();
        String sql = filter.toSql(parameters);

        assertEquals("(dv.version_label = ?)", sql);
        assertEquals(List.of("1' OR '1'='1"), parameters);
        assertFalse(sql.contains("'1'"));
    }

    @Test
    void rendersAndOrGroupsWithPlaceholdersOnly() {
        MetadataFilter filter = MetadataFilterParser.parse(List.of(
                "source_type:in:STATIC,WEB",
                "page:lt:5",
                "source:prefix:https://x.org/100%_done",
                "version:ne:1"
        ), "OR");
        List<Object> parameters = new ArrayList<>();

        String sql = filter.toSql(parameters);

        assertEquals("(d.source_type IN (?, ?) OR COALESCE(c.page_number, (c.metadata->'page'->>'pdf_page_number')::int) < ? "
                + "OR d.source_uri LIKE ? ESCAPE '\\' OR dv.version_label IS DISTINCT FROM ?)", sql);
        assertEquals(List.of("STATIC", "WEB", 5, "https://x.org/100\\%\\_done%", "1"), parameters);
    }

    @Test
    void enforcesConditionAndInListLimits() {
        List<String> tooMany = new ArrayList<>(Collections.nCopies(
                MetadataFilterParser.MAX_CONDITIONS + 1, "page:eq:1"));
        assertThrows(InvalidFilterException.class, () -> MetadataFilterParser.parse(tooMany, null));

        String manyIds = String.join(",", Collections.nCopies(
                MetadataFilterParser.MAX_IN_VALUES + 1, "1"));
        assertThrows(InvalidFilterException.class,
                () -> MetadataFilterParser.parse(List.of("document_id:in:" + manyIds), null));
    }

    @Test
    void describeNeutralisesControlCharactersForLogs() {
        MetadataFilter filter = MetadataFilterParser.parse(
                List.of("version:eq:a\nFAKE LOG LINE"), null);

        assertEquals("version eq a?FAKE LOG LINE", filter.conditions().getFirst().describe());
    }

    @Test
    void filterModeWithoutFiltersIsRejected() {
        assertThrows(InvalidFilterException.class, () -> MetadataFilterParser.parse(null, "OR"));
    }

    @Test
    void validFieldsListsEveryWhitelistedField() {
        var fields = MetadataFilterParser.validFields();

        assertEquals(FilterField.values().length, fields.size());
        assertEquals(List.of("eq", "ne", "in"), fields.get("source_type").get("operators"));
        assertTrue(fields.containsKey("fetch_date"));
    }
}
