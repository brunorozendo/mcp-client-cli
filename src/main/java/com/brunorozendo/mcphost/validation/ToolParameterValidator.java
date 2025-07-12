package com.brunorozendo.mcphost.validation;

import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Validates tool parameters against their schema definitions to ensure
 * required parameters are provided before executing MCP tool calls.
 */
public class ToolParameterValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolParameterValidator.class);
    
    /**
     * Validates the provided arguments against the tool's input schema.
     * 
     * @param tool The MCP tool definition containing the schema
     * @param arguments The arguments provided for the tool call
     * @return ValidationResult containing success status and any error messages
     */
    public static ValidationResult validateToolParameters(McpSchema.Tool tool, Map<String, Object> arguments) {
        if (tool == null) {
            return new ValidationResult(false, List.of("Tool definition is null"));
        }
        
        String toolName = tool.name();
        McpSchema.JsonSchema inputSchema = tool.inputSchema();
        
        // If no schema is defined, any arguments are valid
        if (inputSchema == null) {
            logger.debug("Tool '{}' has no input schema defined, skipping validation", toolName);
            return new ValidationResult(true, List.of());
        }
        
        // If arguments are null but schema exists, create empty map
        Map<String, Object> argsToValidate = arguments != null ? arguments : new HashMap<>();
        
        List<String> errors = new ArrayList<>();
        
        // Check if schema type is "object" (most common case)
        if ("object".equals(inputSchema.type())) {
            // Validate required fields
            List<String> requiredFields = inputSchema.required();
            if (requiredFields != null && !requiredFields.isEmpty()) {
                for (String requiredField : requiredFields) {
                    if (!argsToValidate.containsKey(requiredField) || argsToValidate.get(requiredField) == null) {
                        errors.add(String.format("Missing required parameter '%s' for tool '%s'", requiredField, toolName));
                    }
                }
            }
            
            // Validate properties if defined
            Map<String, Object> properties = inputSchema.properties();
            if (properties != null && properties.containsKey("properties")) {
                Object propsObj = properties.get("properties");
                if (propsObj instanceof Map) {
                    Map<String, Object> propDefs = (Map<String, Object>) propsObj;
                    
                    // Check each provided argument against its schema
                    for (Map.Entry<String, Object> argEntry : argsToValidate.entrySet()) {
                        String argName = argEntry.getKey();
                        Object argValue = argEntry.getValue();
                        
                        if (propDefs.containsKey(argName)) {
                            // Validate the specific property
                            List<String> propErrors = validateProperty(argName, argValue, propDefs.get(argName), toolName);
                            errors.addAll(propErrors);
                        } else {
                            // Check if additional properties are allowed
                            Object additionalProps = properties.get("additionalProperties");
                            if (Boolean.FALSE.equals(additionalProps)) {
                                errors.add(String.format("Unknown parameter '%s' for tool '%s' (additional properties not allowed)", argName, toolName));
                            }
                        }
                    }
                }
            }
        } else if (inputSchema.type() != null) {
            // For non-object types, we would need different validation logic
            logger.warn("Tool '{}' has non-object input schema type '{}', validation not fully implemented", 
                       toolName, inputSchema.type());
        }
        
        boolean isValid = errors.isEmpty();
        if (!isValid) {
            logger.error("Validation failed for tool '{}': {}", toolName, String.join(", ", errors));
        }
        
        return new ValidationResult(isValid, errors);
    }
    
    /**
     * Validates a single property value against its schema definition.
     */
    private static List<String> validateProperty(String propName, Object value, Object propSchema, String toolName) {
        List<String> errors = new ArrayList<>();
        
        if (propSchema instanceof Map) {
            Map<String, Object> schemaMap = (Map<String, Object>) propSchema;
            String type = (String) schemaMap.get("type");
            
            if (type != null && value != null) {
                // Basic type validation
                boolean typeValid = switch (type) {
                    case "string" -> value instanceof String;
                    case "number" -> value instanceof Number;
                    case "integer" -> value instanceof Integer || value instanceof Long;
                    case "boolean" -> value instanceof Boolean;
                    case "array" -> value instanceof List || value instanceof Object[];
                    case "object" -> value instanceof Map;
                    default -> {
                        logger.warn("Unknown type '{}' for property '{}' in tool '{}'", type, propName, toolName);
                        yield true; // Allow unknown types
                    }
                };
                
                if (!typeValid) {
                    errors.add(String.format("Parameter '%s' for tool '%s' should be of type '%s' but got '%s'", 
                              propName, toolName, type, value.getClass().getSimpleName()));
                }
                
                // Validate enum values if specified
                List<Object> enumValues = (List<Object>) schemaMap.get("enum");
                if (enumValues != null && !enumValues.isEmpty() && !enumValues.contains(value)) {
                    errors.add(String.format("Parameter '%s' for tool '%s' must be one of %s but got '%s'", 
                              propName, toolName, enumValues, value));
                }
            }
        }
        
        return errors;
    }
    
    /**
     * Result of parameter validation.
     */
    public static record ValidationResult(boolean isValid, List<String> errors) {
        
        /**
         * Returns a formatted error message containing all validation errors.
         */
        public String getFormattedError() {
            if (errors.isEmpty()) {
                return "";
            }
            return "Validation errors: " + String.join("; ", errors);
        }
    }
}
