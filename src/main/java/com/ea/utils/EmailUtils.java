package com.ea.utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Slf4j
@RequiredArgsConstructor
@Service
public class EmailUtils {

    private final JavaMailSender mailSender;
    private final Props props;

    /**
     * Send an email
     * @param subject the email subject
     * @param content the email content
     * @param to the recipient
     */
    public void sendEmail(String subject, String content, String to)
    {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setFrom(new InternetAddress(props.getEmailUsername(), "MoHH Support"));
            message.setRecipients(MimeMessage.RecipientType.TO, to);
            message.setSubject(subject);
            message.setContent(content, "text/html; charset=utf-8");
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("Error sending email", e);
        }
    }

    /**
     * Get the banner image URL
     * @return the banner image URL
     */
    public String getBanner() {
        return props.getDnsName() + "/images/logo.jpg";
    }

    /**
     * Get the HTML template content
     * @param templateName the template name
     * @return the HTML template content
     */
    public String getHtmlTemplate(String templateName) {
        String templateContent = null;
        Resource templateResource = new ClassPathResource("templates/" + templateName);
        try (InputStream templateStream = templateResource.getInputStream()) {
            templateContent = StreamUtils.copyToString(templateStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Error reading template", e);
        }
        return templateContent;
    }

}
