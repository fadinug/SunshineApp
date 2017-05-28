package com.example.fajar.sunshine.listener;

import com.example.fajar.sunshine.data.Channel;

public interface WeatherServiceListener {
    void serviceSuccess(Channel channel);

    void serviceFailure(Exception exception);
}
