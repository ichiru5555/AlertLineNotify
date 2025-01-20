package org.ichiru;

import com.zaxxer.hikari.HikariDataSource;
import okhttp3.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ichiru.send.LineNotify;

import java.sql.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Earthquake {
    private static final Logger logger = LoggerFactory.getLogger(Earthquake.class);
    private static final String WEBSOCKET_URL = "wss://api.p2pquake.net/v2/ws";
    private static final JsonObject config = DataFile.load("config.json");
    private static final Boolean DEBUG = config.get("debug").getAsBoolean();
    private static final Boolean ERROR_SEND = config.get("error_send").getAsBoolean();
    private static final String TOKEN = DataFile.load("config.json").get("token").getAsString();
    private static HikariDataSource dataSource;
    private static OkHttpClient client;
    private static WebSocket webSocket;
    private static final int RECONNECT_DELAY_SECONDS = 5;

    private static final Map<String, String> typeMap = new HashMap<>() {{
        put("ScalePrompt", "震度速報");
        put("Destination", "震源に関する情報");
        put("ScaleAndDestination", "震度・震源に関する情報");
        put("DetailScale", "各地の震度に関する情報");
        put("Foreign", "遠地地震に関する情報");
        put("Other", "その他の情報");
    }};

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

    private static final Map<String, String> tsunamiMap = new HashMap<>() {{
        put("None", "なし");
        put("Unknown", "不明");
        put("Checking", "調査中");
        put("NonEffective", "若干の海面変動が予想されるが、被害の心配なし");
        put("Watch", "津波注意報");
        put("Warning", "津波予報(種類不明)");
    }};

    private static final Map<String, String> depthMap = new HashMap<>() {{
        put("-1", "不明");
        put("0", "ごく浅い");
    }};

    public static void sendEarthquakeNotification() {
        if (config.get("database_enable").getAsBoolean()) {
            dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(String.format("jdbc:mariadb://%s:%s/%s", config.get("database_host").getAsString(), config.get("database_port").getAsString(), config.get("database_name").getAsString()));
            dataSource.setUsername(config.get("database_username").getAsString());
            dataSource.setPassword(config.get("database_password").getAsString());
            dataSource.addDataSourceProperty("autoReconnect", true);
            dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
            createTable();
        }

        if (config.get("database_enable").getAsBoolean() && DEBUG && dataSource != null) logger.debug("データベースに接続できました");

        if (DEBUG) logger.debug("地震情報の確認を開始");

        client = new OkHttpClient();
        connect();
    }

    private static void connect() {
        Request request = new Request.Builder().url(WEBSOCKET_URL).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                if (DEBUG) logger.debug("WebSocket接続が開きました");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (DEBUG) logger.debug("受信メッセージ: " + text);
                processMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("WebSocket接続に失敗しました", t);
                if (ERROR_SEND) LineNotify.sendNotification(TOKEN, t.getMessage());
                Reconnect();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                logger.warn("WebSocket接続が閉じられました。コード: {} 理由: {}", code, reason);
                if (ERROR_SEND) LineNotify.sendNotification(TOKEN, code+":"+reason);
                if (dataSource != null) dataSource.close();
                client.dispatcher().executorService().shutdown();
            }
        });
    }

    private static void Reconnect() {
        logger.info("{} 秒後に再接続を試みます...", RECONNECT_DELAY_SECONDS);
        client.dispatcher().executorService().execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(RECONNECT_DELAY_SECONDS);
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("再接続の試行が中断されました\n理由は {}", e);
                if (ERROR_SEND) LineNotify.sendNotification(TOKEN, e.getMessage());
            }
        });
    }

    private static void processMessage(String message) {
        JsonObject data = JsonParser.parseString(message).getAsJsonObject();
        int code = data.get("code").getAsInt();
        if (code != 551 && code != 552 && code != 554 && code != 556) {
            if(DEBUG) logger.debug("コードが551, 552, 554, 556でないため通知しません");
            return;
        }

        String id = data.get("_id").getAsString();
        String publisher = data.get("issue").getAsJsonObject().get("source").getAsString();
        String announcementTime = data.get("issue").getAsJsonObject().get("time").getAsString();
        String presentationType = data.get("issue").getAsJsonObject().get("type").getAsString();
        String earthquakeTime =  data.get("earthquake").getAsJsonObject().get("time").getAsString();
        String earthquakeName = data.get("earthquake").getAsJsonObject().get("hypocenter").getAsJsonObject().get("name").getAsString();
        String depth = data.get("earthquake").getAsJsonObject().get("hypocenter").getAsJsonObject().get("depth"). getAsString();
        String magnitude = data.get("earthquake").getAsJsonObject().get("hypocenter").getAsJsonObject().get("magnitude").getAsString();
        String earthquakeIntensity = data.get("earthquake").getAsJsonObject().get("maxScale").getAsString();
        String tsunami = data.get("earthquake").getAsJsonObject().get("domesticTsunami").getAsString();
        String prefectures = data.get("points").getAsJsonArray().get(0).getAsJsonObject().get("pref").getAsString();

        presentationType = typeMap.getOrDefault(presentationType, "不明");
        tsunami = tsunamiMap.getOrDefault(tsunami, "不明");

        if (config.get("database_enable").getAsBoolean()){
            String sql = "INSERT INTO earthquake (Occurrence_time, earthquake_intensity, prefectures, observatory, magnitude, depth, tsunami, date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.parse(earthquakeTime, formatter)));
                pstmt.setInt(2, Integer.parseInt(earthquakeIntensity));
                pstmt.setString(3, prefectures);
                pstmt.setString(4, earthquakeName);
                pstmt.setDouble(5, Double.parseDouble(magnitude));
                pstmt.setInt(6, Integer.parseInt(depth));
                pstmt.setString(7, tsunami);
                pstmt.setDate(8, Date.valueOf(LocalDate.now()));
                pstmt.executeUpdate();
                if (DEBUG) logger.debug("データの保存に成功しました");
            } catch (SQLException e) {
                logger.error("データ保存中にエラーが発生しました: {}", e.getMessage());
                if (ERROR_SEND) LineNotify.sendNotification(TOKEN, e.getMessage());
            }
        }

        if (!earthquakeIntensity.equals("不明")) {
            if (Integer.parseInt(earthquakeIntensity) < 40) {
                if (DEBUG) logger.debug("震度が4未満のため通知しません");
                return;
            }
        }

        if (Integer.parseInt(magnitude) == -1)
            magnitude = "不明";
        else if (earthquakeName.isEmpty())
            earthquakeName = "不明";

        if(depth.matches(".*\\d.*"))
            depth = depth+"Km";

        String messageContent = String.format(
                "\n%s\n発表元: %s\n発表時間: %s\n地震発生時間: %s\n地震発生場所: %s\n震源の深さ: %s\n震度: %s\nマグニチュード: %s\n地震の津波: %s\n都道府県: %s",
                presentationType, publisher, announcementTime, earthquakeTime, earthquakeName, depthMap.getOrDefault(depth, depth), intensityMap.getOrDefault(earthquakeIntensity, "不明"),
                magnitude, tsunami, prefectures
        );

        LineNotify.sendNotification(TOKEN, messageContent);
    }

    private static void createTable() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, "earthquake", null)) {
                if (rs.next()) {
                    return;
                }
                String createTableQuery = "CREATE TABLE earthquake ("
                        + "id int(11) NOT NULL AUTO_INCREMENT,"
                        + "Occurrence_time datetime NOT NULL,"
                        + "earthquake_intensity int(11) NOT NULL,"
                        + "prefectures text NOT NULL,"
                        + "observatory text NOT NULL,"
                        + "magnitude double NOT NULL,"
                        + "depth int(11) NOT NULL,"
                        + "tsunami text NOT NULL,"
                        + "date date NOT NULL,"
                        + "PRIMARY KEY (id)"
                        + ")";
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(createTableQuery);
                    if (DEBUG) logger.debug("テーブル earthquake を生成しました");
                } catch (SQLException e) {
                    logger.error(e.getMessage());
                    LineNotify.sendNotification(TOKEN, e.getMessage());
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
            LineNotify.sendNotification(TOKEN, e.getMessage());
        }
    }
}
