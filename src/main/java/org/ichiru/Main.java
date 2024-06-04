package org.ichiru;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class.getName());

    private static final JsonObject config = DataFile.load("config.json");

    public static void main(String[] args) {
        File configfile = new File("config.json");
        File datafile = new File("data.json");
        if (!configfile.exists()){
            DataFile.create_config();
            return;
        } else if (!datafile.exists()) {
            logger.info("データファイルがないためデータファイルを作成しました");
            DataFile.create_data();
        }

        if (config.get("debug").getAsBoolean()) logger.debug("デバッグモードが有効です。");
        DataFile.CheckConfig();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        scheduler.scheduleAtFixedRate(WeatherAlarm::fetchAndProcessData, 0, 5, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(Earthquake::sendEarthquakeNotification, 0, 5, TimeUnit.SECONDS);
        DailyTaskScheduler dailyTaskScheduler = new DailyTaskScheduler(scheduler);
        dailyTaskScheduler.scheduleDailyTask(WeatherTemperature::notifyIfExceedsThreshold, config.get("Weather_hours").getAsInt(), config.get("Weather_minutes").getAsInt());
    }
}
