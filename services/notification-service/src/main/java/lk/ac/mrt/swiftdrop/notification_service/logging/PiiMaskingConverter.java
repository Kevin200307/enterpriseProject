package lk.ac.mrt.swiftdrop.notification_service.logging;

import ch.qos.logback.core.pattern.CompositeConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback composite converter that redacts Personally Identifiable Information (PII)
 * such as email addresses and phone numbers from every log message written by the service.
 */
public class PiiMaskingConverter extends CompositeConverter<ILoggingEvent> {

    // Regex pattern for Email addresses
    private static final Pattern EMAIL_PATTERN = 
            Pattern.compile("(?i)\\b([a-zA-Z0-9_.+-])[a-zA-Z0-9_.+-]*@([a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+)\\b");

    // Regex pattern for Phone numbers (e.g. +94771234567 or 0771234567)
    private static final Pattern PHONE_PATTERN = 
            Pattern.compile("(\\+?[0-9]{3,4})[0-9]{3,6}([0-9]{3,4})");

    @Override
    protected String transform(ILoggingEvent event, String in) {
        if (in == null || in.isEmpty()) {
            return in;
        }

        // 1. Mask Email Addresses: local-part redacted (e.g. thiran@gmail.com -> th****@gmail.com)
        Matcher emailMatcher = EMAIL_PATTERN.matcher(in);
        StringBuilder sb = new StringBuilder();
        while (emailMatcher.find()) {
            String firstChar = emailMatcher.group(1);
            String domain = emailMatcher.group(2);
            emailMatcher.appendReplacement(sb, firstChar + "****@" + domain);
        }
        emailMatcher.appendTail(sb);

        // 2. Mask Phone Numbers (e.g. +94771234567 -> +947****4567)
        Matcher phoneMatcher = PHONE_PATTERN.matcher(sb.toString());
        StringBuilder phoneSb = new StringBuilder();
        while (phoneMatcher.find()) {
            String prefix = phoneMatcher.group(1);
            String suffix = phoneMatcher.group(2);
            phoneMatcher.appendReplacement(phoneSb, prefix + "****" + suffix);
        }
        phoneMatcher.appendTail(phoneSb);

        return phoneSb.toString();
    }

    /**
     * Helper method to mask an email string directly.
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.isEmpty()) {
            return "****@" + domain;
        }
        if (name.length() <= 2) {
            return name.charAt(0) + "****@" + domain;
        }
        return name.substring(0, 2) + "****@" + domain;
    }

    /**
     * Helper method to mask a phone number string directly.
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        int len = phone.length();
        String prefix = phone.substring(0, Math.min(4, len - 4));
        String suffix = phone.substring(len - 4);
        return prefix + "****" + suffix;
    }
}