package org.ichiru;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DailyTaskScheduler {
    private static final Logger logger = LoggerFactory.getLogger(DailyTaskScheduler.class);
    private final ScheduledExecutorService scheduler;
    private static final Boolean DEBUG = DataFile.load("config.json").get("debug").getAsBoolean();
    public DailyTaskScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public void scheduleDailyTask(Runnable task, int Hour, int Minute) {
        Calendar now = Calendar.getInstance();
        Calendar firstRun = Calendar.getInstance();
        firstRun.set(Calendar.HOUR_OF_DAY, Hour);
        firstRun.set(Calendar.MINUTE, Minute);
        firstRun.set(Calendar.SECOND, 0);
        firstRun.set(Calendar.MILLISECOND, 0);

        if (firstRun.before(now)) {
            firstRun.add(Calendar.DAY_OF_MONTH, 1);
        }
        long initialDelay = firstRun.getTimeInMillis() - now.getTimeInMillis();
        long period = TimeUnit.DAYS.toMillis(1);

        if (DEBUG) logger.debug("初回実行までの遅延時間: {} ミリ秒", initialDelay);
        if (DEBUG) logger.debug("タスクを毎日 {} 時 {} 分に実行します", Hour, Minute);

        scheduler.scheduleAtFixedRate(() -> {
            if (DEBUG) logger.debug("タスクを実行します");
            task.run();
        }, initialDelay, period, TimeUnit.MILLISECONDS);
    }
}
