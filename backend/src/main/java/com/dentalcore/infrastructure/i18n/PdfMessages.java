package com.dentalcore.infrastructure.i18n;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

/**
 * Localized strings for PDF rendering, backed by the standard Spring
 * {@link ResourceBundleMessageSource} convention: one bundle per language at
 * {@code classpath:messages/pdf_<lang>.properties} (UTF-8; the Spanish bundle
 * uses {@code \\uXXXX} escapes so it stays correct under any file encoding).
 *
 * <p>Date rendering is localized too: each bundle carries its own
 * {@code pdf.date.pattern} / {@code pdf.datetime.pattern} and the formatter is
 * built with the language's {@link Locale} so month names localize.
 */
@Component
public class PdfMessages {

    private final ResourceBundleMessageSource source;

    public PdfMessages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages/pdf");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        this.source = messageSource;
    }

    /** Resolves a message; with args the message is a {@link java.text.MessageFormat}. */
    public String get(String lang, String key, Object... args) {
        return source.getMessage(key, args.length == 0 ? null : args, locale(lang));
    }

    public String date(String lang, LocalDate date) {
        return dateFormatter(lang).format(date);
    }

    public DateTimeFormatter dateFormatter(String lang) {
        return DateTimeFormatter.ofPattern(get(lang, "pdf.date.pattern"), locale(lang));
    }

    public String dateTime(String lang, TemporalAccessor instant, ZoneId zone) {
        return DateTimeFormatter.ofPattern(get(lang, "pdf.datetime.pattern"), locale(lang))
                .withZone(zone)
                .format(instant);
    }

    private Locale locale(String lang) {
        return Locale.forLanguageTag(lang);
    }
}
