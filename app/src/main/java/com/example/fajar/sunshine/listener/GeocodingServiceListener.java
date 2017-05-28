package com.example.fajar.sunshine.listener;

import com.example.fajar.sunshine.data.LocationResult;

public interface GeocodingServiceListener {
    void geocodeSuccess(LocationResult location);

    void geocodeFailure(Exception exception);
}
