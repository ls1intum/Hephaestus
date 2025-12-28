package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.activity.badpracticedetector.BadPracticesDetectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for bad practice detection events and sends notification emails.
 */
@Component
public class BadPracticeNotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeNotificationListener.class);

    private final MailService mailService;

    public BadPracticeNotificationListener(MailService mailService) {
        this.mailService = mailService;
    }

    @Async
    @EventListener
    public void onBadPracticesDetected(BadPracticesDetectedEvent event) {
        logger.info(
            "Received bad practices detected event for PR #{} with {} bad practices",
            event.pullRequest().number(),
            event.badPractices().size()
        );
        mailService.sendBadPracticesDetectedEmail(event);
    }
}
