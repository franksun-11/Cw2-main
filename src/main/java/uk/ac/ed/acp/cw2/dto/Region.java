package uk.ac.ed.acp.cw2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Region definition class
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Region {
    @NotNull(message = "Region name cannot be null")
    @JsonProperty("name")
    private String name;

    @NotNull(message = "Vertices cannot be null")
    @JsonProperty("vertices")
    private List<LngLat> vertices;

    /**
     * Check if the region is closed (first and last vertex are the same)
     */
    public boolean isClosed() {
        if (vertices == null || vertices.size() < 3) {
            return false;
        }
        LngLat first = vertices.get(0);
        LngLat last = vertices.get(vertices.size() - 1);
        return first.getLng().equals(last.getLng()) &&
                first.getLat().equals(last.getLat());
    }
}