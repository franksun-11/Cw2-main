package uk.ac.ed.acp.cw2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.ac.ed.acp.cw2.pojo.LngLat;
import uk.ac.ed.acp.cw2.pojo.Region;

/**
 * isInRegion RequestBody
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsInRegionRequest {
    @NotNull(message = "Position cannot be null")
    @Valid
    @JsonProperty("position")
    private LngLat position;

    @NotNull(message = "Region cannot be null")
    @Valid
    @JsonProperty("region")
    private Region region;
}