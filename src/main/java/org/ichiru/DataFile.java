package org.ichiru;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataFile {
    private static final Logger logger = LoggerFactory.getLogger(DataFile.class.getName());

    public static JsonObject load(String filename) {
        try (FileReader reader = new FileReader(filename)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            logger.error("設定ファイルの読み込み中にエラーが発生しました: " + e.getMessage());
            return new JsonObject();
        }
    }

    public static void save(String filename, JsonObject config) {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(config.toString());
        } catch (IOException e) {
            logger.error("設定ファイルの保存中にエラーが発生しました: " + e.getMessage());
        }
    }
    public static void create_config() {
        JsonObject defaultConfig = new JsonObject();
        defaultConfig.addProperty("token", "");
        defaultConfig.addProperty("Weather_station", "");
        defaultConfig.addProperty("debug", false);

        save("config.json", defaultConfig);
        logger.info("デフォルトの設定ファイルを作成しました。");
    }
    public  static void create_data() {
        JsonObject defaultData = new JsonObject();
        defaultData.addProperty("earthquake_id", "");
        defaultData.addProperty("Alarm_id", "");
        save("data.json", defaultData);
        logger.info("データ保存ファイルを作成しました。");
    }
}
