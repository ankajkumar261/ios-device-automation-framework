package com.portfolio.ios.core;

import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Allure;
import org.openqa.selenium.OutputType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Failure diagnostics: screenshot plus page source, attached to the Allure
 * report and mirrored to disk.
 *
 * <p>Why both. The screenshot shows what a human saw; the accessibility page
 * source shows what the framework could actually <i>see</i>. When an
 * onboarding test fails, the useful question is almost never "what did the
 * screen look like" — it is "which elements were in the tree, and with what
 * labels". A screenshot cannot answer that, and this app has no accessibility
 * identifiers, so its labels are the only selectors the suite has.
 */
public final class Artifacts {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private Artifacts() {
    }

    /**
     * Captures everything useful about the current state.
     *
     * <p>Deliberately never throws. Diagnostics that can fail a test are worse
     * than no diagnostics at all — a screenshot timeout on a wedged session
     * would mask the real failure with a misleading one.
     *
     * @param label short name for the artifact, e.g. the phase or step name
     */
    public static void capture(IOSDriver driver, String label) {
        String base = sanitise(label) + "-" + LocalDateTime.now().format(STAMP);
        try {
            attach(base + ".png", "image/png", screenshotPng(driver));
        } catch (RuntimeException e) {
            System.err.println("[iosautomation] screenshot capture failed: " + e.getMessage());
        }
        try {
            attach(base + ".xml", "text/xml", pageSource(driver).getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            System.err.println("[iosautomation] page-source capture failed: " + e.getMessage());
        }
    }

    private static byte[] screenshotPng(IOSDriver driver) {
        // OutputType.BYTES rather than the base64 string: it skips an encode /
        // decode round trip and cannot corrupt on a partial read.
        return driver.getScreenshotAs(OutputType.BYTES);
    }

    private static String pageSource(IOSDriver driver) {
        return driver.getPageSource();
    }

    /**
     * Attaches bytes to the current Allure step and writes a disk copy.
     *
     * <p>Allure's API only accepts an {@link java.io.InputStream} for binary
     * content, so the bytes are wrapped rather than passed directly.
     */
    private static void attach(String fileName, String mimeType, byte[] content) {
        try (InputStream stream = new ByteArrayInputStream(content)) {
            Allure.addAttachment(fileName, mimeType, stream,
                    fileName.substring(fileName.lastIndexOf('.') + 1));
        } catch (IOException e) {
            System.err.println("[iosautomation] could not attach " + fileName + " to Allure: " + e);
        } catch (RuntimeException e) {
            // Allure is not listening in a plain unit run; the disk copy still matters.
        }
        Path target = Config.artifactsDir().resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            System.err.println("[iosautomation] could not write artifact " + target + ": " + e.getMessage());
        }
    }

    /** Keeps file names safe and readable on every filesystem. */
    private static String sanitise(String raw) {
        if (raw == null || raw.isBlank()) {
            return "capture";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }
}