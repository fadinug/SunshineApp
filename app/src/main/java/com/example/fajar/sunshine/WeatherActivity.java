package com.example.fajar.sunshine;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.fajar.sunshine.R;
import com.example.fajar.sunshine.data.Channel;
import com.example.fajar.sunshine.data.Condition;
import com.example.fajar.sunshine.data.LocationResult;
import com.example.fajar.sunshine.data.Units;
import com.example.fajar.sunshine.fragments.WeatherConditionFragment;
import com.example.fajar.sunshine.listener.GeocodingServiceListener;
import com.example.fajar.sunshine.listener.WeatherServiceListener;
import com.example.fajar.sunshine.service.WeatherCacheService;
import com.example.fajar.sunshine.service.GoogleMapsGeocodingService;
import com.example.fajar.sunshine.service.YahooWeatherService;

public class WeatherActivity extends AppCompatActivity implements WeatherServiceListener, GeocodingServiceListener, LocationListener {

    public static int GET_WEATHER_FROM_CURRENT_LOCATION = 0x00001;

    private ImageView weatherIconImageView;
    private TextView temperatureTextView;
    private TextView conditionTextView;
    private TextView locationTextView;

    private YahooWeatherService weatherService;
    private GoogleMapsGeocodingService geocodingService;
    private WeatherCacheService cacheService;

    private ProgressDialog loadingDialog;

    private boolean weatherServicesHasFailed = false;

    private SharedPreferences preferences = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        setTitle("Sunshine");

        weatherIconImageView = (ImageView) findViewById(R.id.weatherIconImageView);
        temperatureTextView = (TextView) findViewById(R.id.temperatureTextView);
        conditionTextView = (TextView) findViewById(R.id.conditionTextView);
        locationTextView = (TextView) findViewById(R.id.locationTextView);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        weatherService = new YahooWeatherService(this);
        weatherService.setTemperatureUnit(preferences.getString(getString(R.string.pref_temperature_unit), null));

        geocodingService = new GoogleMapsGeocodingService(this);
        cacheService = new WeatherCacheService(this);

        if (preferences.getBoolean(getString(R.string.pref_needs_setup), true)) {
            startSettingsActivity();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage(getString(R.string.loading));
        loadingDialog.setCancelable(false);
        loadingDialog.show();

        String location = null;

        if (preferences.getBoolean(getString(R.string.pref_geolocation_enabled), true)) {
            String locationCache = preferences.getString(getString(R.string.pref_cached_location), null);

            if (locationCache == null) {
                getWeatherFromCurrentLocation();
            } else {
                location = locationCache;
            }
        } else {
            location = preferences.getString(getString(R.string.pref_manual_location), null);
        }

        if (location != null) {
            weatherService.refreshWeather(location);
        }
    }

    private void getWeatherFromCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
            }, GET_WEATHER_FROM_CURRENT_LOCATION);

            return;
        }

        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        Criteria locationCriteria = new Criteria();

        if (isNetworkEnabled) {
            locationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        } else if (isGPSEnabled) {
            locationCriteria.setAccuracy(Criteria.ACCURACY_FINE);
        }

        locationManager.requestSingleUpdate(locationCriteria, this, null);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == WeatherActivity.GET_WEATHER_FROM_CURRENT_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getWeatherFromCurrentLocation();
            } else {
                loadingDialog.hide();

                AlertDialog messageDialog = new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.location_permission_needed))
                        .setPositiveButton(getString(R.string.disable_geolocation), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                startSettingsActivity();
                            }
                        })
                        .create();

                messageDialog.show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    private void startSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.currentLocation:
                loadingDialog.show();
                getWeatherFromCurrentLocation();
                return true;
            case R.id.settings:
                startSettingsActivity();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void serviceSuccess(Channel channel) {
        loadingDialog.hide();

        Condition condition = channel.getItem().getCondition();
        Units units = channel.getUnits();
        Condition[] forecast = channel.getItem().getForecast();

        int weatherIconImageResource = getResources().getIdentifier("icon_" + condition.getCode(), "drawable", getPackageName());

        weatherIconImageView.setImageResource(weatherIconImageResource);
        temperatureTextView.setText(getString(R.string.temperature_output, condition.getTemperature(), units.getTemperature()));
        conditionTextView.setText(condition.getDescription());
        locationTextView.setText(channel.getLocation());

        for (int day = 0; day < forecast.length; day++) {
            if (day >= 5) {
                break;
            }

            Condition currentCondition = forecast[day];

            int viewId = getResources().getIdentifier("forecast_" + day, "id", getPackageName());
            WeatherConditionFragment fragment = (WeatherConditionFragment) getSupportFragmentManager().findFragmentById(viewId);

            if (fragment != null) {
                fragment.loadForecast(currentCondition, channel.getUnits());
            }
        }

        cacheService.save(channel);
    }

    @Override
    public void serviceFailure(Exception exception) {
        if (weatherServicesHasFailed) {
            loadingDialog.hide();
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        } else {
            weatherServicesHasFailed = true;
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();

            cacheService.load(this);
        }
    }

    @Override
    public void geocodeSuccess(LocationResult location) {
        weatherService.refreshWeather(location.getAddress());

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(getString(R.string.pref_cached_location), location.getAddress());
        editor.apply();
    }

    @Override
    public void geocodeFailure(Exception exception) {
        cacheService.load(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        geocodingService.refreshLocation(location);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}
