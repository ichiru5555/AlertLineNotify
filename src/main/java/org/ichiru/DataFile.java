package org.ichiru;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        defaultConfig.addProperty("mail_enable", false);
        defaultConfig.addProperty("mail_host", "");
        defaultConfig.addProperty("mail_port", "465");
        defaultConfig.addProperty("mail_username", "");
        defaultConfig.addProperty("mail_password", "");
        defaultConfig.addProperty("database_enable", false);
        defaultConfig.addProperty("database_host", "localhost");
        defaultConfig.addProperty("database_port", "3306");
        defaultConfig.addProperty("database_name", "");
        defaultConfig.addProperty("database_username", "");
        defaultConfig.addProperty("database_password", "");
        defaultConfig.addProperty("exchange_enable", false);
        JsonArray recipientsArray = new JsonArray();
        for (String email: new String[]{"mailaddress", "mailaddress"}) {
            recipientsArray.add(email);
        }
        defaultConfig.add("mail_recipients", recipientsArray);
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
        List<String> errors = new ArrayList<>();
        try {
            if (!config.has("token") || config.get("token").getAsString().isEmpty())
                errors.add("トークンが登録されていません");
            if (config.get("Weather_city_id").getAsString().isEmpty())
                errors.add("市町村のコードが指定されていません");
            if (config.get("Weather_station").getAsString().isEmpty())
                errors.add("気象台が指定されていません");
            if (config.get("mail_host").getAsString().isEmpty())
                errors.add("メールホストが指定されていません");
            if (config.get("mail_username").getAsString().isEmpty())
                errors.add("メールユーザー名が指定されていません");
            if (config.get("mail_password").getAsString().isEmpty())
                errors.add("メールパスワードが指定されていません");
            if (config.get("database_host").getAsString().isEmpty())
                errors.add("データベースホスト名が指定されていません");
            if (config.get("database_port").getAsString().isEmpty())
                errors.add("データベースポートが指定されていません");
            if (config.get("database_name").getAsString().isEmpty())
                errors.add("データベース名が指定されていません");
            if (config.get("database_username").getAsString().isEmpty())
                errors.add("データベースユーザー名が指定されていません");
            if (config.get("database_password").getAsString().isEmpty())
                errors.add("データベースパスワードが指定されていません");
        } catch (NullPointerException e) {
            errors.add(e.getMessage());
        }
        if (!errors.isEmpty()) {
            for (String error : errors) {
                logger.error(error);
            }
            System.exit(1);
        }
    }
}
