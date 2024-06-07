package org.ichiru;

import okhttp3.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Earthquake {
    private static final Logger logger = LoggerFactory.getLogger(Earthquake.class);
    private static final String WEBSOCKET_URL = "wss://api.p2pquake.net/v2/ws";
    private static final Boolean DEBUG = DataFile.load("config.json").get("debug").getAsBoolean();

    // 地震情報のタイプを日本語に変換するためのマップ
    private static final Map<String, String> typeMap = new HashMap<>() {{
        put("ScalePrompt", "震度速報");
        put("Destination", "震源に関する情報");
        put("ScaleAndDestination", "震度・震源に関する情報");
        put("DetailScale", "各地の震度に関する情報");
        put("Foreign", "遠地地震に関する情報");
        put("Other", "その他の情報");
    }};

    // 震度の情報を日本語に変換するためのマップ
    private static final Map<String, String> intensityMap = new HashMap<>() {{
        put("-1", "震度情報なし");
        put("10", "震度1");
        put("20", "震度2");
        put("30", "震度3");
        put("40", "震度4");
        put("45", "震度5弱");
        put("50", "震度5強");
        put("55", "震度6弱");
        put("60", "震度6強");
        put("70", "震度7");
    }};

    // 津波の情報を日本語に変換するためのマップ
    private static final Map<String, String> tsunamiMap = new HashMap<>() {{
        put("None", "なし");
        put("Unknown", "不明");
        put("Checking", "調査中");
        put("NonEffective", "若干の海面変動が予想されるが、被害の心配なし");
        put("Watch", "津波注意報");
        put("Warning", "津波予報(種類不明)");
    }};

    // 地震通知を送信するメソッド
    public static void sendEarthquakeNotification() {
        logger.info("地震情報の確認を開始");
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(WEBSOCKET_URL).build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                logger.info("WebSocket接続が開きました");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                logger.info("受信メッセージ: " + text);
                processMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("WebSocket接続に失敗しました", t);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                logger.warn("WebSocket接続が閉じられました。コード: {} 理由: {}", code, reason);
                client.dispatcher().executorService().shutdown();
            }
        });
    }

    private static void processMessage(String message) {
        JsonObject data = JsonParser.parseString(message).getAsJsonObject();
        // フィルタリング: codeが551, 552, 554, 556でない場合は通知を送信しない
        int code = data.get("code").getAsInt();
        if (code != 551 && code != 552 && code != 554 && code != 556) {
            if(DEBUG) logger.debug("コードが551, 552, 554, 556でないため通知しません");
            return;
        }

        String id = data.has("id") ? data.get("id").getAsString() : UUID.randomUUID().toString().replace("-", "");
        String publisher = data.has("issue") && data.get("issue").getAsJsonObject().has("source") ? data.get("issue").getAsJsonObject().get("source").getAsString() : "不明";
        String announcementTime = data.has("issue") && data.get("issue").getAsJsonObject().has("time") ? data.get("issue").getAsJsonObject().get("time").getAsString() : "不明";
        String presentationType = data.has("issue") && data.get("issue").getAsJsonObject().has("type") ? data.get("issue").getAsJsonObject().get("type").getAsString() : "不明";
        String earthquakeTime = data.has("earthquake") && data.get("earthquake").getAsJsonObject().has("time") ? data.get("earthquake").getAsJsonObject().get("time").getAsString() : "不明";
        String earthquakeName = data.has("earthquake") && data.get("earthquake").getAsJsonObject().has("hypocenter") && data.get("earthquake").getAsJsonObject().get("hypocenter").getAsJsonObject().has("name") ? data.get("earthquake").getAsJsonObject().get("hypocenter").getAsJsonObject().get("name").getAsString() : "不明";
        String depth = data.has("earthquake") && data.get("earthquake").getAsJsonObject().has("hypocenter") && data.get("earthquake").getAsJsonObject().get("hypocenter").getAsJsonObject().has("depth") ? data.get("earthquake").getAsJsonObject().get("hypocenter").getAsJsonObject().get("depth").getAsString() : "不明";
        String magnitude = data.has("earthquake") && data.get("earthquake").getAsJsonObject().has("hypocenter") && data.get("earthquake").getAsJsonObject().get("hypocenter").getAsJsonObject().has("magnitude") ? data.get("earthquake").getAsJsonObject().get("hypocenter").getAsJsonObject().get("magnitude").getAsString() : "不明";
        String earthquakeIntensity = data.has("earthquake") && data.get("earthquake").getAsJsonObject().has("maxScale") ? data.get("earthquake").getAsJsonObject().get("maxScale").getAsString() : "不明";
        String tsunami = data.has("earthquake") && data.get("earthquake").getAsJsonObject().has("domesticTsunami") ? data.get("earthquake").getAsJsonObject().get("domesticTsunami").getAsString() : "不明";
        String prefectures = data.has("points") && !data.get("points").getAsJsonArray().isEmpty() && data.get("points").getAsJsonArray().get(0).getAsJsonObject().has("pref") ? data.get("points").getAsJsonArray().get(0).getAsJsonObject().get("pref").getAsString() : "不明";

        // 震度が4未満の場合は通知を送信しない
        if (earthquakeIntensity != null && !earthquakeIntensity.equals("不明")) {
            int intensity = Integer.parseInt(earthquakeIntensity);
            if (intensity < 40) {
                if (DEBUG) logger.debug("震度が4未満のため通知しません");
                return;
            }
        }

        JsonObject data_file = DataFile.load("data.json");
        if (id.equals(data_file.get("earthquake_id").getAsString())) {
            if (DEBUG) logger.debug("地震IDが同じなため通知しません");
            return;
        }
        data_file.addProperty("earthquake_id", id);
        DataFile.save("data.json", data_file);
        if (DEBUG) logger.debug("地震ID {} を data.json に保存しました", id);

        presentationType = typeMap.getOrDefault(presentationType, "不明");
        earthquakeIntensity = intensityMap.getOrDefault(earthquakeIntensity, "不明");
        tsunami = tsunamiMap.getOrDefault(tsunami, "不明");

        String messageContent = String.format(
                "\n%s\n発表元: %s\n発表時間: %s\n地震発生時間: %s\n地震発生場所: %s\n震源の深さ: %sKm\n震度: %s\nマグニチュード: %s\n地震の津波: %s\n都道府県: %s",
                presentationType, publisher, announcementTime, earthquakeTime, earthquakeName, depth, earthquakeIntensity,
                magnitude, tsunami, prefectures
        );

        boolean success = LineNotify.sendNotification(DataFile.load("config.json").get("token").getAsString(), messageContent);
        if (!success) {
            logger.warn("地震情報の通知に失敗しました");
        }
    }
}
