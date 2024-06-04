package org.ichiru;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LineNotify {
    private static final Logger logger = LoggerFactory.getLogger(LineNotify.class.getName());
    private static final OkHttpClient client = new OkHttpClient();

    public static boolean sendNotification(String token, String messageContent) {
        RequestBody body = new FormBody.Builder()
                .add("message", messageContent)
                .build();

        Request request = new Request.Builder()
                .url("https://notify-api.line.me/api/notify")
                .post(body)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.info("LINE通知に成功しました");
                return true;
            } else {
                logger.warn("LINE通知に失敗しました HTTPステータスコード: " + response.code());
                return false;
            }
        } catch (IOException e) {
            logger.error("LINE通知中にエラーが発生しました: " + e.getMessage());
            return false;
        }
    }
}
