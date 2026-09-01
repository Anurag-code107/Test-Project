package com.tenxengage.app.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean enabled;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from}") String fromAddress,
                        @Value("${app.mail.enabled}") boolean enabled) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.enabled = enabled;
    }

    @Async("taskExecutor")
    public void sendApprovalEmail(String toEmail, String incentiveName,
                                  String approveUrl, String rejectUrl, String reviewUrl) {
        String subject = "Approval Required: " + incentiveName;
        String html = buildApprovalHtml(incentiveName, approveUrl, rejectUrl, reviewUrl);

        if (!enabled) {
            log.info("Mail disabled — would send approval email to {}", toEmail);
            log.info("  Subject: {}", subject);
            log.info("  Review URL: {}", reviewUrl);
            log.info("  Approve URL: {}", approveUrl);
            log.info("  Reject URL: {}", rejectUrl);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Approval email sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send approval email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async("taskExecutor")
    public void sendOnboardingEmail(String toEmail, String firstName, String onboardingUrl) {
        String subject = "Welcome to TenX Engage - Complete Your Setup";

        if (!enabled) {
            log.info("Mail disabled — would send onboarding email to {}", toEmail);
            log.info("  Subject: {}", subject);
            log.info("  Onboarding URL: {}", onboardingUrl);
            return;
        }

        String html = buildOnboardingHtml(firstName, onboardingUrl);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Onboarding email sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send onboarding email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildOnboardingHtml(String firstName, String onboardingUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; \
            max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9fafb;">
              <div style="background: white; border-radius: 12px; padding: 32px; \
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);">
                <div style="text-align: center; margin-bottom: 24px;">
                  <h2 style="color: #111827; margin: 0 0 8px;">Welcome to TenX Engage</h2>
                  <p style="color: #6b7280; margin: 0;">You've been invited to join an incentive program</p>
                </div>
                <p style="color: #4b5563; line-height: 1.6;">
                  Hi %s,
                </p>
                <p style="color: #4b5563; line-height: 1.6;">
                  Your account has been created on TenX Engage. To get started, please complete
                  your account setup by clicking the button below. You'll set your password,
                  complete your profile, and review the applicable policies.
                </p>
                <div style="text-align: center; margin: 32px 0;">
                  <a href="%s" style="display: inline-block; padding: 14px 40px; \
            background-color: #4f46e5; color: white; text-decoration: none; border-radius: 8px; \
            font-weight: 600; font-size: 16px;">Complete Your Setup</a>
                </div>
                <div style="text-align: center; margin-bottom: 24px;">
                  <p style="color: #6b7280; font-size: 13px; margin: 0 0 8px;">Or copy and paste this link into your browser:</p>
                  <a href="%s" style="color: #4f46e5; font-size: 13px; word-break: break-all;">%s</a>
                </div>
                <p style="color: #9ca3af; font-size: 13px; text-align: center;">
                  This link expires in 7 days. If you did not expect this email, please ignore it.
                </p>
              </div>
            </body>
            </html>
            """.formatted(firstName, onboardingUrl, onboardingUrl, onboardingUrl);
    }

    private String buildApprovalHtml(String incentiveName, String approveUrl, String rejectUrl, String reviewUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; \
            max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9fafb;">
              <div style="background: white; border-radius: 12px; padding: 32px; \
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);">
                <div style="text-align: center; margin-bottom: 24px;">
                  <h2 style="color: #111827; margin: 0 0 8px;">Approval Required</h2>
                  <p style="color: #6b7280; margin: 0;">An incentive program needs your review</p>
                </div>
                <div style="background: #f3f4f6; border-radius: 8px; padding: 16px; margin-bottom: 24px;">
                  <p style="color: #374151; margin: 0; font-weight: 600;">%s</p>
                </div>
                <p style="color: #4b5563; line-height: 1.6;">
                  You have been designated as an approver for this incentive program.
                  Please review and take action by clicking the link below.
                </p>
                <div style="text-align: center; margin: 32px 0;">
                  <a href="%s" style="display: inline-block; padding: 14px 40px; \
            background-color: #4f46e5; color: white; text-decoration: none; border-radius: 8px; \
            font-weight: 600; font-size: 16px;">Review &amp; Decide</a>
                </div>
                <div style="text-align: center; margin-bottom: 24px;">
                  <p style="color: #6b7280; font-size: 13px; margin: 0 0 8px;">Or copy and paste this link into your browser:</p>
                  <a href="%s" style="color: #4f46e5; font-size: 13px; word-break: break-all;">%s</a>
                </div>
                <p style="color: #9ca3af; font-size: 13px; text-align: center;">
                  This link expires in 7 days. If you did not expect this email, please ignore it.
                </p>
              </div>
            </body>
            </html>
            """.formatted(incentiveName, reviewUrl, reviewUrl, reviewUrl);
    }
}
