package com.duotail.utils.email.sender.mcp;

import com.duotail.utils.email.sender.permission.PermissionException;
import org.apache.commons.lang3.Strings;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final String JSON_RPC_VERSION = "2.0";

    private final McpToolService mcpToolService;
    private final ObjectMapper objectMapper;

    public McpController(McpToolService mcpToolService, ObjectMapper objectMapper) {
        this.mcpToolService = mcpToolService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(@RequestBody McpRequest request) {
        if (!Strings.CS.equals(request.jsonrpc(), JSON_RPC_VERSION)) {
            return rpcError(request.id(), -32600, "Invalid Request: jsonrpc must be 2.0");
        }

        try {
            return switch (request.method()) {
                case "initialize" -> rpcResult(request.id(), Map.of(
                        "protocolVersion", "2025-03-26",
                        "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "duotail-test-email-mcp", "version", "1.0.0")
                ));
                case "tools/list" -> rpcResult(request.id(), Map.of("tools", mcpToolService.listTools()));
                case "tools/call" -> handleToolsCall(request);
                default -> rpcError(request.id(), -32601, "Method not found: " + request.method());
            };
        } catch (PermissionException ex) {
            return toolErrorResult(request.id(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return rpcError(request.id(), -32602, ex.getMessage());
        } catch (Exception ex) {
            return rpcError(request.id(), -32000, ex.getMessage());
        }
    }

    private Map<String, Object> handleToolsCall(McpRequest request) throws Exception {
        var params = objectMapper.convertValue(request.params(), ToolCallParams.class);
        var arguments = params.arguments() == null ? Map.<String, Object>of() : params.arguments();
        var result = mcpToolService.callTool(params.name(), arguments);
        return rpcResult(request.id(), result);
    }

    private Map<String, Object> rpcResult(JsonNode id, Object result) {
        var response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", JSON_RPC_VERSION);
        response.put("id", objectMapper.convertValue(id, Object.class));
        response.put("result", result);
        return response;
    }

    private Map<String, Object> toolErrorResult(JsonNode id, String message) {
        return rpcResult(id, Map.of(
                "content", List.of(Map.of("type", "text", "text", message)),
                "isError", true
        ));
    }

    private Map<String, Object> rpcError(JsonNode id, int code, String message) {
        var response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", JSON_RPC_VERSION);
        response.put("id", objectMapper.convertValue(id, Object.class));
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    public record McpRequest(String jsonrpc, JsonNode id, String method, JsonNode params) {
    }

    public record ToolCallParams(String name, Map<String, Object> arguments) {
    }
}



