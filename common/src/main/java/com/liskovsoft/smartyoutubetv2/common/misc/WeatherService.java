package com.liskovsoft.smartyoutubetv2.common.misc;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.okhttp.OkHttpManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherService {
    private static final long CACHE_DURATION_MS = 30 * 60 * 1_000L;
    private static WeatherResult sCachedResult;
    private static String sCachedLocation;
    private static long sCachedAtMs;

    public interface Listener {
        void onResult(WeatherResult result);
        void onError();
    }

    public static class WeatherResult {
        public final double temperature;
        public final int weatherCode;
        public final int rainProbability;

        WeatherResult(double temperature, int weatherCode, int rainProbability) {
            this.temperature = temperature;
            this.weatherCode = weatherCode;
            this.rainProbability = rainProbability;
        }
    }

    public static void load(String location, Listener listener) {
        long now = System.currentTimeMillis();
        if (sCachedResult != null && location.equals(sCachedLocation) && now - sCachedAtMs < CACHE_DURATION_MS) {
            listener.onResult(sCachedResult);
            return;
        }

        HttpUrl url = HttpUrl.parse("https://geocoding-api.open-meteo.com/v1/search").newBuilder()
                .addQueryParameter("name", location)
                .addQueryParameter("count", "1")
                .addQueryParameter("language", "zh")
                .addQueryParameter("format", "json")
                .build();
        enqueue(url, body -> {
            JSONObject root = new JSONObject(body);
            JSONArray results = root.optJSONArray("results");
            if (results == null || results.length() == 0) {
                notifyError(location, listener);
                return;
            }

            JSONObject place = results.getJSONObject(0);
            loadForecast(location, place.getDouble("latitude"), place.getDouble("longitude"), listener);
        }, () -> notifyError(location, listener));
    }

    private static void loadForecast(String location, double latitude, double longitude, Listener listener) {
        HttpUrl url = HttpUrl.parse("https://api.open-meteo.com/v1/forecast").newBuilder()
                .addQueryParameter("latitude", String.valueOf(latitude))
                .addQueryParameter("longitude", String.valueOf(longitude))
                .addQueryParameter("current", "temperature_2m,weather_code")
                .addQueryParameter("hourly", "precipitation_probability")
                .addQueryParameter("forecast_hours", "1")
                .addQueryParameter("timezone", "auto")
                .build();
        enqueue(url, body -> {
            JSONObject root = new JSONObject(body);
            JSONObject current = root.getJSONObject("current");
            JSONArray rain = root.getJSONObject("hourly").getJSONArray("precipitation_probability");
            WeatherResult result = new WeatherResult(
                    current.getDouble("temperature_2m"),
                    current.getInt("weather_code"),
                    rain.length() > 0 ? rain.getInt(0) : 0);
            sCachedResult = result;
            sCachedLocation = location;
            sCachedAtMs = System.currentTimeMillis();
            listener.onResult(result);
        }, () -> notifyError(location, listener));
    }

    private interface ResponseListener {
        void onResponse(String body) throws Exception;
    }

    private static void enqueue(HttpUrl url, ResponseListener responseListener, Runnable errorListener) {
        Request request = new Request.Builder().url(url).get().build();
        OkHttpManager.instance().getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                errorListener.run();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response ignored = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        errorListener.run();
                        return;
                    }
                    responseListener.onResponse(response.body().string());
                } catch (Exception e) {
                    errorListener.run();
                }
            }
        });
    }

    private static void notifyError(String location, Listener listener) {
        if (sCachedResult != null && location.equals(sCachedLocation)) {
            listener.onResult(sCachedResult);
        } else {
            listener.onError();
        }
    }
}
