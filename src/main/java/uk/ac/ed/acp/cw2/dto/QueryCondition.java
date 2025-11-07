// src/main/java/uk/ac/ed/acp/cw2/dto/QueryCondition.java
package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Represents a single query condition for dynamic drone filtering
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryCondition {

    @JsonProperty("attribute")
    private String attribute;

    // Comparison operator (=, !=, <, >)
    @JsonProperty("operator")
    private String operator;

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
