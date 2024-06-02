package org.ichiru;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Exchange {
    private static final Logger logger = LoggerFactory.getLogger(Exchange.class.getName());
    private static final String URL = "https://query1.finance.yahoo.com/v8/finance/chart/USDJPY=X?range=1h";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Boolean DEBUG = DataFile.load("config.json").get("debug").getAsBoolean();

    /**
     * CheckPriceメソッドは、USD/JPYの現在価格を取得し、前回から10円以上変動していれば通知します
     */
    public static void CheckPrice() {
        if (DEBUG) logger.debug("価格チェックを開始します。");
        Request request = new Request.Builder()
                .url(URL)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("予期しないコード " + response);

            String responseBody = response.body().string();
            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
            // 現在の価格を取得
            double currentPrice = jsonObject.getAsJsonObject("chart")
                    .getAsJsonArray("result")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("meta")
                    .get("regularMarketPrice")
                    .getAsDouble();
            JsonObject DATA_PREVIOUSPRICE = DataFile.load("data.json");
            double previousPrice = DATA_PREVIOUSPRICE.get("previousPrice").getAsDouble();
            if (DEBUG) logger.debug("現在の価格: " + currentPrice + "円, 前回の価格: " + previousPrice + "円");
            // 現在の価格が前回の価格より10円以上高ければLINE通知を送る
            if (currentPrice > previousPrice + 10) {
                LineNotify.sendNotification(DataFile.load("config.json").get("token").getAsString(),
                        "\n1ドル当たり: " + currentPrice + "円");
            }
            DATA_PREVIOUSPRICE.addProperty("previousPrice", currentPrice);
            DataFile.save("data.json", DATA_PREVIOUSPRICE);
            if (DEBUG) logger.debug("価格情報を更新しました。");
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
