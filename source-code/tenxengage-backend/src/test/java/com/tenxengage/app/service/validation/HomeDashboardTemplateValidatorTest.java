package com.tenxengage.app.service.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.model.HomeDashboardLayoutPayload;
import com.tenxengage.app.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HomeDashboardTemplateValidatorTest {

    private HomeDashboardTemplateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HomeDashboardTemplateValidator(new ObjectMapper());
    }

    @Test
    void validateRoleType_acceptsInternalAndExternal() {
        validator.validateRoleType("INTERNAL");
        validator.validateRoleType("EXTERNAL");
    }

    @Test
    void validateRoleType_rejectsUnknown() {
        assertThatThrownBy(() -> validator.validateRoleType("BOTH"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("INTERNAL or EXTERNAL");
        assertThatThrownBy(() -> validator.validateRoleType(null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void parseAndValidate_acceptsClientAdminTemplate() {
        String json = """
                {"rows":[
                  {"layout":"full","slots":[{"widgetKey":"ai_assistant"}]},
                  {"layout":"full","slots":[{"widgetKey":"program_performance"}]}
                ]}""";

        HomeDashboardLayoutPayload payload = validator.parseAndValidate(json, "INTERNAL");

        assertThat(payload.rows()).hasSize(2);
        assertThat(payload.rows().get(0).layout()).isEqualTo("full");
        assertThat(payload.rows().get(0).slots().get(0).widgetKey()).isEqualTo("ai_assistant");
    }

    @Test
    void parseAndValidate_acceptsPartnerUserTemplateWithHalfHalfRow() {
        String json = """
                {"rows":[
                  {"layout":"half-half","slots":[
                    {"widgetKey":"ai_assistant"},{"widgetKey":"rewards_balances"}
                  ]},
                  {"layout":"full","slots":[{"widgetKey":"tenx_suggestions"}]}
                ]}""";

        HomeDashboardLayoutPayload payload = validator.parseAndValidate(json, "EXTERNAL");

        assertThat(payload.rows()).hasSize(2);
        assertThat(payload.rows().get(0).slots()).hasSize(2);
    }

    @Test
    void parseAndValidate_rejectsMalformedJson() {
        assertThatThrownBy(() -> validator.parseAndValidate("not json", "INTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid layout JSON");
    }

    @Test
    void parseAndValidate_rejectsEmptyRows() {
        assertThatThrownBy(() -> validator.parseAndValidate("{\"rows\":[]}", "INTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one row");
    }

    @Test
    void parseAndValidate_rejectsNullRows() {
        assertThatThrownBy(() -> validator.parseAndValidate("{}", "INTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one row");
    }

    @Test
    void parseAndValidate_rejectsUnknownLayout() {
        String json = "{\"rows\":[{\"layout\":\"thirds\",\"slots\":[{\"widgetKey\":\"ai_assistant\"}]}]}";
        assertThatThrownBy(() -> validator.parseAndValidate(json, "INTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unknown layout: thirds");
    }

    @Test
    void parseAndValidate_rejectsSlotCountMismatch_tooFew() {
        String json = "{\"rows\":[{\"layout\":\"half-half\",\"slots\":[{\"widgetKey\":\"ai_assistant\"}]}]}";
        assertThatThrownBy(() -> validator.parseAndValidate(json, "EXTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expects 2 slot(s), got 1");
    }

    @Test
    void parseAndValidate_rejectsSlotCountMismatch_tooMany() {
        String json = "{\"rows\":[{\"layout\":\"full\",\"slots\":["
                + "{\"widgetKey\":\"ai_assistant\"},{\"widgetKey\":\"program_performance\"}]}]}";
        assertThatThrownBy(() -> validator.parseAndValidate(json, "INTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expects 1 slot(s), got 2");
    }

    @Test
    void parseAndValidate_rejectsUnknownWidget() {
        String json = "{\"rows\":[{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"mystery_widget\"}]}]}";
        assertThatThrownBy(() -> validator.parseAndValidate(json, "INTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unknown widget 'mystery_widget'");
    }

    @Test
    void parseAndValidate_rejectsMissingWidgetKey() {
        String json = "{\"rows\":[{\"layout\":\"full\",\"slots\":[{\"widgetKey\":null}]}]}";
        assertThatThrownBy(() -> validator.parseAndValidate(json, "INTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("missing widgetKey");
    }

    @Test
    void parseAndValidate_rejectsBlankWidgetKey() {
        String json = "{\"rows\":[{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"  \"}]}]}";
        assertThatThrownBy(() -> validator.parseAndValidate(json, "INTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("missing widgetKey");
    }

    @Test
    void parseAndValidate_rejectsWidgetNotSupportedByRoleType() {
        // rewards_balances is EXTERNAL only; applying in an INTERNAL template must fail.
        String json = "{\"rows\":[{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"rewards_balances\"}]}]}";
        assertThatThrownBy(() -> validator.parseAndValidate(json, "INTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not support role type INTERNAL");
    }

    @Test
    void parseAndValidate_rejectsInternalWidgetInExternalTemplate() {
        String json = "{\"rows\":[{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"program_performance\"}]}]}";
        assertThatThrownBy(() -> validator.parseAndValidate(json, "EXTERNAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not support role type EXTERNAL");
    }

    @Test
    void parseAndValidate_acceptsApproverTemplate() {
        String json = "{\"rows\":[{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"approvals\"}]}]}";
        HomeDashboardLayoutPayload payload = validator.parseAndValidate(json, "INTERNAL");
        assertThat(payload.rows()).hasSize(1);
    }

    @Test
    void parseAndValidate_acceptsAiAssistantInBothRoleTypes() {
        String json = "{\"rows\":[{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"ai_assistant\"}]}]}";
        validator.parseAndValidate(json, "INTERNAL");
        validator.parseAndValidate(json, "EXTERNAL");
    }
}
