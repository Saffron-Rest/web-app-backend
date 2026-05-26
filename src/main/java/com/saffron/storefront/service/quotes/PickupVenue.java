package com.saffron.storefront.service.quotes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Restaurant pickup location, sourced from {@code app.pickup.*} config. */
@Component
public class PickupVenue {

    private final String name;
    private final String address;
    private final double lat;
    private final double lng;
    private final String phone;
    private final String country;

    public PickupVenue(
            @Value("${app.pickup.name}") String name,
            @Value("${app.pickup.address}") String address,
            @Value("${app.pickup.lat}") double lat,
            @Value("${app.pickup.lng}") double lng,
            @Value("${app.pickup.phone}") String phone,
            @Value("${app.pickup.country:PL}") String country) {
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.phone = phone;
        this.country = country;
    }

    public String name() { return name; }
    public String address() { return address; }
    public double lat() { return lat; }
    public double lng() { return lng; }
    public String phone() { return phone; }
    public String country() { return country; }
}
