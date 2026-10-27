package lk.ac.mrt.swiftdrop.pricing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Locale;
import java.util.Objects;

@Embeddable
public class ZoneCity {
    @Column(name = "city_name", nullable = false, length = 120)
    private String cityName;

    @Column(name = "normalized_city", nullable = false, length = 120)
    private String normalizedCity;

    protected ZoneCity() {}

    public ZoneCity(String cityName) {
        this.cityName = cityName.trim();
        this.normalizedCity = this.cityName.toLowerCase(Locale.ROOT);
    }

    public String getCityName() { return cityName; }
    public String getNormalizedCity() { return normalizedCity; }

    @Override public boolean equals(Object other) {
        return other instanceof ZoneCity city && normalizedCity.equals(city.normalizedCity);
    }
    @Override public int hashCode() { return Objects.hash(normalizedCity); }
}