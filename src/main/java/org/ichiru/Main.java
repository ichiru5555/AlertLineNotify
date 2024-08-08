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

        if (config.get("debug").getAsBoolean()) logger.debug("デバッグモードが有効です");
        DataFile.CheckConfig();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        if (config.get("Earthquake_enable").getAsBoolean()) {
            scheduler.submit(Earthquake::sendEarthquakeNotification);
        } else if (config.get("WeatherAlarm_enable").getAsBoolean()) {
            scheduler.scheduleAtFixedRate(WeatherAlarm::fetchAndProcessData, 0, 5, TimeUnit.MINUTES);
        } else if (config.get("WeatherTemperature_enable").getAsBoolean()){
            TaskScheduler Taskscheduler = new TaskScheduler(scheduler);
            Taskscheduler.Daily(WeatherTemperature::notifyIfExceedsThreshold, config.get("Weather_hours").getAsInt(), config.get("Weather_minutes").getAsInt());
        } else {
            System.exit(1);
            logger.error("機能が有効になっていないためプログラムは終了します");
        }
    }
}
