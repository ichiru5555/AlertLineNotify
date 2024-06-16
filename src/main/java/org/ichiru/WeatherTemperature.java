package org.ichiru;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ichiru.notifi.LineNotify;
import org.ichiru.notifi.Mail;

import java.io.IOException;

//最高気温が特定の値を超えると通知
public class WeatherTemperature {
    private static final Logger logger = LoggerFactory.getLogger(WeatherTemperature.class);
    private static final JsonObject config = DataFile.load("config.json");
    private static final Boolean DEBUG = config.get("debug").getAsBoolean();
    public static void notifyIfExceedsThreshold() {
        if (DEBUG) logger.debug("最高気温が特定の値を超えているか確認します");
        if (!config.has("Weather_city_id")){
            logger.info("city_idが見つからないため作成します");
            config.addProperty("Weather_city_id", "");
            DataFile.save("config.json", config);
            return;
        } else if (!config.has("Weather_max_temp")) {
            logger.info("maxTempが見つからないため作成します");
            config.addProperty("Weather_max_temp", 30);
            DataFile.save("config.json", config);
        }
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://weather.tsukumijima.net/api/forecast?city=" + config.get("city_id"))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
                JsonObject forecasts = jsonObject.getAsJsonArray("forecasts").get(1).getAsJsonObject();
                JsonObject temperature = forecasts.getAsJsonObject("temperature");
                JsonObject max = temperature.getAsJsonObject("max");
                int maxTemp = max.get("celsius").getAsInt();
                if (config.get("Weather_max_temp").getAsInt() <= maxTemp){
                    LineNotify.sendNotification(DataFile.load("config.json").get("token").getAsString(), "\n最高気温がしきい値を超えました\n最高気温: " + maxTemp);
                    Mail.send("最高気温がしきい値を超過", "\n最高気温がしきい値を超えました\n最高気温: " + maxTemp);
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
