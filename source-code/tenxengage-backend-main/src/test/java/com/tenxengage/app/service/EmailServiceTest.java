package com.tenxengage.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendApprovalEmail_doesNotSendWhenDisabled() {
        EmailService emailService = new EmailService(mailSender, "from@test.com", false);

        emailService.sendApprovalEmail("to@test.com", "Test Incentive",
                "https://approve", "https://reject", "https://review");

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendOnboardingEmail_doesNotSendWhenDisabled() {
        EmailService emailService = new EmailService(mailSender, "from@test.com", false);

        emailService.sendOnboardingEmail("to@test.com", "John", "https://onboard");

        verify(mailSender, never()).createMimeMessage();
    }
}
