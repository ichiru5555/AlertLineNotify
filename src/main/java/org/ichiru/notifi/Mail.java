package org.ichiru.notifi;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ichiru.DataFile;

import java.util.List;
import java.util.Properties;

public class Mail {
    private static final Logger logger = LoggerFactory.getLogger(Mail.class);
    private static final JsonObject config = DataFile.load("config.json");
    private static final Boolean DEBUG = config.get("debug").getAsBoolean();
    public static void send(String subject, String content){
        String host = config.get("mail_host").getAsString();
        String port = config.get("mail_port").getAsString();
        String username = config.get("mail_username").getAsString();
        String password = config.get("mail_password").getAsString();
        List<String> recipients = new Gson().fromJson(config.get("mail_recipients"), new TypeToken<List<String>>(){}.getType());
        if (host.isEmpty() || port.isEmpty() || username.isEmpty() || password.isEmpty()){
            logger.error("必要なメール情報が指定されていません");
            return;
        }
        if (DEBUG) logger.debug("送信先メールアドレスのチェック");
        for (String recipient: recipients){
            if (!recipient.contains("@")){
                logger.warn("{} はメールアドレスではないため除外します", recipient);
                recipients.remove(recipient);
            }
        }
        if (recipients.isEmpty()){
            logger.error("送信先メールアドレスがないため処理を終了します");
            return;
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);

        Session session = Session.getInstance(props, new jakarta.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            for (String recipient: recipients) {
                message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            }
            message.setSubject(subject);
            message.setText(content);
            Transport.send(message);
            if (DEBUG) logger.debug("メールが送信されました");
        } catch (MessagingException e) {
            logger.error(e.getMessage());
        }
    }
}
