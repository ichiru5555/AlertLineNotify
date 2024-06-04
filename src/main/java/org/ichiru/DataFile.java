package org.ichiru;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public static void save(String filename, JsonObject data) {
        try (FileWriter writer = new FileWriter(filename)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String prettyJsonString = gson.toJson(data);
            writer.write(prettyJsonString);
        } catch (IOException e) {
            logger.error("設定ファイルの保存中にエラーが発生しました: " + e.getMessage());
        }
    }
    public static void create_config() {
        JsonObject defaultConfig = new JsonObject();
        defaultConfig.addProperty("token", "");
        defaultConfig.addProperty("debug", false);
        defaultConfig.addProperty("Weather_city_id", "");
        defaultConfig.addProperty("Weather_station", "");
        defaultConfig.addProperty("Weather_hours", 7);
        defaultConfig.addProperty("Weather_minutes", 0);
        defaultConfig.addProperty("Weather_max_temp", 30);
        save("config.json", defaultConfig);
        logger.info("デフォルトの設定ファイルを作成しました");
    }
    public static void create_data() {
        JsonObject defaultData = new JsonObject();
        defaultData.addProperty("earthquake_id", "");
        defaultData.addProperty("Alarm_id", "");
        save("data.json", defaultData);
        logger.info("データ保存ファイルを作成しました");
    }
    public static void CheckConfig(){
        JsonObject config = load("config.json");
        if (config.get("debug").getAsBoolean()) logger.debug("configファイルのチェックします");
        if (!config.has("token") || config.get("token").getAsString().isEmpty()){
            logger.error("トークンが登録されていません");
            System.exit(2);
        } else if (config.get("Weather_city_id").getAsString().isEmpty()) {
            logger.error("市町村のコードが指定されていません");
            System.exit(2);
        } else if (config.get("Weather_station").getAsString().isEmpty()) {
            logger.error("気象台が指定されていません");
            System.exit(2);
        }
    }
}
