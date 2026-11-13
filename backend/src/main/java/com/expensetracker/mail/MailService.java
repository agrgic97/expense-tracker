package com.expensetracker.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Sends plain-text mails via SMTP, if a mail server is configured. */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> sender, @Value("${spring.mail.host:}") String host,
                       @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.host = host;
        this.from = from;
    }

    public boolean enabled() {
        return StringUtils.hasText(host);
    }

    /**
     * Sends in the background, so callers answer equally fast whether or not a mail goes out and a slow mail
     * server does not block the request.
     */
    @Async
    public void send(String to, String subject, String text) {
        if (!enabled()) {
            log.warn("Mail an {} nicht gesendet: MAIL_HOST ist nicht konfiguriert", to);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(from)) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.getObject().send(message);
        } catch (MailException e) {
            log.error("Mail an {} konnte nicht gesendet werden", to, e);
        }
    }
}
