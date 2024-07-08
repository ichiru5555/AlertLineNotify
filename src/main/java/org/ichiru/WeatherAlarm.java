package org.ichiru;

import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ichiru.send.LineNotify;

import java.io.IOException;

public class WeatherAlarm {
    private static final Logger logger = LoggerFactory.getLogger(WeatherAlarm.class);
    private static final String XML_URL = "https://www.data.jma.go.jp/developer/xml/feed/extra.xml";
    private static final OkHttpClient client = new OkHttpClient();
    private static final String WEATHER_STATION = DataFile.load("config.json").get("Weather_station").getAsString();
    private static final Boolean DEBUG = DataFile.load("config.json").get("debug").getAsBoolean();
    private static final Boolean ERROR_SEND = DataFile.load("config.json").get("error_send").getAsBoolean();

    // データの取得と処理を行うメソッド
    public static void fetchAndProcessData() {
        if (DEBUG) logger.debug("警報の確認を開始しました");

        // ステップ1: 初期XMLを取得
        String initialXml = fetchXml(XML_URL);
        if (initialXml == null) {
            logger.error("初期XMLの取得に失敗しました");
            return;
        }
        if (DEBUG) logger.debug("初期XMLの取得に成功しました");

        // ステップ2: 初期XMLを解析し、最大5つのURLを取得
        StringBuilder resultMessage = new StringBuilder();
        if (DEBUG) logger.debug("初期XMLを解析しています");
        Elements entryUrls = parseInitialXml(initialXml, resultMessage);
        if (DEBUG) logger.debug("初期XMLの解析が完了しました");
        if (entryUrls.isEmpty()) {
            return;
        }
        if (DEBUG) logger.debug("解析されたエントリーURL: " + entryUrls.size() + " 件");

        // ステップ3: 各URLを取得し、結果をメッセージに追加
        String previousAlarmId = DataFile.load("data.json").get("Alarm_id").getAsString();
        boolean allKindNameAreWarnings = true;

        for (Element url : entryUrls) {
            String entryId = url.text();

            // URLが既に処理されているか確認
            if (entryId.equals(previousAlarmId)) {
                if (DEBUG) logger.debug("エントリーID {} は既に処理されています", entryId);
                continue;
            }

            // エントリーIDを config.json に保存
            JsonObject data = DataFile.load("data.json");
            data.addProperty("Alarm_id", entryId);
            DataFile.save("data.json", data);
            if (DEBUG) logger.debug("エントリーID {} を data.json に保存しました", entryId);

            // 二次XMLを取得
            String secondaryXml = fetchXml(entryId);
            if (secondaryXml == null) {
                logger.error("二次XMLの取得に失敗しました");
                continue;
            }
            if (DEBUG) logger.debug("二次XMLの取得に成功しました: " + entryId);

            // 二次XMLを解析し、警報が含まれているかを確認
            boolean hasAlert = parseSecondaryXml(secondaryXml, resultMessage);
            if (hasAlert) {
                allKindNameAreWarnings = false;
            }
        }

        // 結果がすべて注意報でない場合は通知を送信
        if (!allKindNameAreWarnings) {
            if (DEBUG) logger.debug("通知を送信しています...");
            LineNotify.sendNotification(DataFile.load("config.json").get("token").getAsString(), resultMessage.toString());
            if (DEBUG) logger.debug("通知が送信されました");
        }
        if (DEBUG) logger.debug("警報の確認が完了しました");
    }

    // 指定されたURLからXMLを取得するメソッド
    private static String fetchXml(String url) {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                if (DEBUG) logger.debug("XMLの読み取りに成功しました" );
                return response.body().string();
            } else {
                logger.warn("URL " + url + " からXMLの取得に失敗しました。HTTPステータス: " + response.code());
            }
        } catch (IOException e) {
            logger.error("URL " + url + " からXMLの取得エラー: " + e.getMessage());
            if (ERROR_SEND) LineNotify.sendNotification(DataFile.load("config.json").get("token").getAsString(), url+": "+e.getMessage());
        }
        return null;
    }

    // 初期XMLを解析し、エントリーURLを抽出するメソッド
    private static Elements parseInitialXml(String xml, StringBuilder resultMessage) {
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        Elements entries = doc.select("entry");
        Elements entryUrls = new Elements();
        int count = 0;
        for (Element entry : entries) {
            if (count >= 5) {
                break;
            }
            String authorName = entry.selectFirst("author > name").text();
            String title = entry.selectFirst("title").text();
            if (title.startsWith("気象警報・注意報")) {
                continue;
            }
            if (WEATHER_STATION.equals(authorName)) {
                Element idElement = entry.selectFirst("id");
                entryUrls.add(idElement);
                resultMessage.append("\n参照URL: ").append(idElement.text()).append("\n");
                if (DEBUG) logger.debug("解析中のエントリー: " + idElement.text());
            }
            count++;
        }

        return entryUrls;
    }

    // 二次XMLを解析し、結果メッセージに追加するメソッド
    private static boolean parseSecondaryXml(String xml, StringBuilder resultMessage) {
        Document doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
        String title = doc.selectFirst("Head > Title").text();
        String text = doc.selectFirst("Head > Headline > Text").text();
        if (text.contains("注意報")){
            if (DEBUG) logger.debug("注意報のため終了");
            return false;
        }

        resultMessage.append(title).append("\n");
        resultMessage.append(text).append("\n");

        Elements informations = doc.select("Head > Headline > Information[type=気象警報・注意報（市町村等をまとめた地域等）] > Item");
        boolean hasAlert = false;

        for (Element info : informations) {
            String kindName = info.selectFirst("Kind > Name").text();
            resultMessage.append(kindName).append("\n");
            if (DEBUG) logger.debug("解析中のKindName: " + kindName);

            if (!kindName.contains("注意報")) {
                hasAlert = true;
            }

            Elements areas = info.select("Areas > Area");
            for (Element area : areas) {
                resultMessage.append(area.selectFirst("Name").text()).append("\n");
                resultMessage.append("----").append("\n");
                if (DEBUG) logger.debug("解析中のエリア名: " + area.selectFirst("Name").text());
            }
        }
        return hasAlert;
    }
}
