package eu.eahub.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GeoLocation {
    private double latitude;
    private double longitude;
    private String country;
}