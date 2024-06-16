package org.ichiru.keep;

import com.zaxxer.hikari.HikariDataSource;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ichiru.DataFile;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Calendar;
import java.sql.Date;

public class Exchange {
    private static final Logger logger = LoggerFactory.getLogger(Exchange.class);
    private static final String URL = "https://query1.finance.yahoo.com/v8/finance/chart/USDJPY=X?range=1h";
    private static final OkHttpClient client = new OkHttpClient();
    private static final JsonObject config = DataFile.load("config.json");
    private static final Boolean DEBUG = config.get("debug").getAsBoolean();
    static {
        if (DEBUG) {
            System.setProperty("hikariLogLevel", "DEBUG");
        } else {
            System.setProperty("hikariLogLevel", "INFO");
        }
    }
    public static void CheckPrice() {
        if (!config.get("database_enable").getAsBoolean() && !config.get("exchange_enable").getAsBoolean()) {
            return;
        }

        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(String.format("jdbc:mariadb://%s:%s/%s",
                    config.get("database_host").getAsString(),
                    config.get("database_port").getAsString(),
                    config.get("database_name").getAsString()));
            dataSource.setUsername(config.get("database_username").getAsString());
            dataSource.setPassword(config.get("database_password").getAsString());
            dataSource.addDataSourceProperty("autoReconnect", "true");
            dataSource.setDriverClassName("org.mariadb.jdbc.Driver");

            if (DEBUG) logger.debug("価格チェックを開始します。");

            Request request = new Request.Builder().url(URL).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("予期しないコード " + response);
                }

                String responseBody = response.body().string();
                JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

                JsonObject chart = jsonObject.getAsJsonObject("chart");
                if (chart == null) {
                    throw new IllegalStateException("チャート情報が見つかりません。");
                }

                JsonObject result = chart.getAsJsonArray("result").get(0).getAsJsonObject();
                JsonObject meta = result.getAsJsonObject("meta");
                if (meta == null) {
                    throw new IllegalStateException("メタ情報が見つかりません。");
                }

                double currentPrice = meta.get("regularMarketPrice").getAsDouble();

                String sql = "INSERT INTO exchange (price, date) VALUES (?, ?)";

                try (Connection conn = dataSource.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {

                    Calendar calendar = Calendar.getInstance();
                    Date date = new Date(calendar.getTime().getTime());

                    pstmt.setDouble(1, currentPrice);
                    pstmt.setDate(2, date);

                    pstmt.executeUpdate();
                    if (DEBUG) logger.debug("データが正常に保存されました");
                } catch (SQLException e) {
                    logger.error("データ保存中にエラーが発生しました: ", e);
                }
            } catch (IOException e) {
                logger.error("HTTPリクエスト中にエラーが発生しました: ", e);
            }
        } catch (Exception e) {
            logger.error("データソースの設定中にエラーが発生しました: ", e);
        }
    }
    public static void delete(){
        if (!config.get("database_enable").getAsBoolean() && !config.get("exchange_enable").getAsBoolean()) {
            return;
        }
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(String.format("jdbc:mariadb://%s:%s/%s",
                    config.get("database_host").getAsString(),
                    config.get("database_port").getAsString(),
                    config.get("database_name").getAsString()));
            dataSource.setUsername(config.get("database_username").getAsString());
            dataSource.setPassword(config.get("database_password").getAsString());
            dataSource.addDataSourceProperty("autoReconnect", "true");
            dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("TRUNCATE TABLE exchange")) {
                int affectedRows = pstmt.executeUpdate();
                if (DEBUG) logger.debug("exchange テーブルのすべてのデータを削除しました。影響を受けた行数: {}", affectedRows);
            } catch (SQLException e) {
                logger.error("exchange テーブルのデータ削除中にエラーが発生しました: ", e);
            }
        } catch (Exception e) {
            logger.error("データソースの設定中にエラーが発生しました: ", e);
        }
    }
}

