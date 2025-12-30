// src/main/java/uk/ac/ed/acp/cw2/dto/QueryCondition.java
package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Represents a single query condition for dynamic drone filtering
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryCondition {

    @NotBlank(message = "Attribute cannot be null or empty")
    @JsonProperty("attribute")
    private String attribute;

    // Comparison operator (=, !=, <, >)
    @NotBlank(message = "Operator cannot be null or empty")
    @JsonProperty("operator")
    private String operator;

    @NotBlank(message = "Value cannot be null or empty")
    @JsonProperty("value")
    private String value;

    @Override
    public String toString() {
        return "QueryCondition{" +
                "attribute='" + attribute + '\'' +
                ", operator='" + operator + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
