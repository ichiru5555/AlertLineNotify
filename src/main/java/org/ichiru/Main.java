package org.ichiru;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class.getName());

    public static void main(String[] args) {
        File configfile = new File("config.json");
        File datafile = new File("data.json");
        if (!configfile.exists() & !datafile.exists()){
            DataFile.create_config();
            DataFile.create_data();
            return;
        }

        if (DataFile.load("config.json").get("debug").getAsBoolean()) logger.debug("デバッグモードが有効です。");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
        Alarm alarm = new Alarm();

        scheduler.scheduleAtFixedRate(alarm::fetchAndProcessData, 0, 5, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(Earthquake::sendEarthquakeNotification, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(Exchange::CheckPrice, 0, 3, TimeUnit.HOURS);
    }
}
