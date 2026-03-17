package com.duotail.utils.email.sender.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpControllerTest {

    private McpToolService mcpToolService;
    private McpController mcpController;

    @BeforeEach
    void setUp() {
        mcpToolService = mock(McpToolService.class);
        mcpController = new McpController(mcpToolService, new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    @Test
    void initializeReturnsMcpServerMetadata() {
        var request = new McpController.McpRequest("2.0", null, "initialize", null);

        var response = mcpController.handle(request);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertEquals("duotail-test-email-mcp", serverInfo.get("name"));
        assertEquals("2025-03-26", result.get("protocolVersion"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void toolsListReturnsRegisteredTools() {
        when(mcpToolService.listTools()).thenReturn(java.util.List.of(Map.of("name", "send_email")));
        var request = new McpController.McpRequest("2.0", null, "tools/list", null);

        var response = mcpController.handle(request);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        var tools = (java.util.List<Map<String, Object>>) result.get("tools");
        assertEquals("send_email", tools.getFirst().get("name"));
    }

    @Test
    void invalidToolCallReturnsJsonRpcValidationError() throws Exception {
        when(mcpToolService.callTool("unknown", Map.of()))
                .thenThrow(new IllegalArgumentException("Unknown tool: unknown"));

        var params = new ObjectMapper().valueToTree(Map.of("name", "unknown", "arguments", Map.of()));
        var request = new McpController.McpRequest("2.0", null, "tools/call", params);

        var response = mcpController.handle(request);

        @SuppressWarnings("unchecked") Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertEquals(-32602, error.get("code"));
        assertEquals("Unknown tool: unknown", error.get("message"));
    }
}


