package com.portfolio.ios.core;

import org.testng.IConfigurationListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sorts every failure into what most likely caused it, so a red run is
 * triaged in seconds instead of by opening each test:
 * <ul>
 *   <li>{@link Kind#UI_CHANGED} — an element or screen the test needs was not
 *       found (a wait timed out, a scroll never reached it). Usually the app's
 *       wording or layout changed; on iOS 27 devices also the known
 *       background-image accessibility issue.</li>
 *   <li>{@link Kind#ENVIRONMENT} — Appium, WebDriverAgent, the device or the
 *       configuration, not the app.</li>
 *   <li>{@link Kind#PRegistryBLE_BUG} — the screen was there but a check did not
 *       hold (an assertion). Look at it as a phealthcareble app bug first; a
 *       presence check written as an assertion can also land here.</li>
 * </ul>
 * The classification is a triage hint read from the exception, not a verdict.
 *
 * <p>Output: one console line per failure, a summary when the suite ends, the
 * same summary in {@code target/failure-triage.txt}, and Allure's
 * "Categories" tab grouped the same way ({@code categories.json} in the
 * results folder). Registered for every suite through
 * {@code META-INF/services/org.testng.ITestNGListener}.
 */
public class FailureClassifier implements ITestListener, IConfigurationListener, ISuiteListener {

    public enum Kind {
        UI_CHANGED("UI changed — element or screen not found"),
        ENVIRONMENT("Environment — Appium, device or configuration"),
        PRegistryBLE_BUG("Phealthcareble bug — a check did not hold"),
        OTHER("Other failure");

        final String title;

        Kind(String title) {
            this.title = title;
        }
    }

    private static final Pattern ENVIRONMENT = Pattern.compile(
            "SessionNotCreatedException|UnreachableBrowserException|ECONNREFUSED|socket hang up"
                    + "|Could not proxy command|xcodebuild|devicectl|Missing required configuration"
                    + "|No iPhone (?:or iPad )?is connected|is not connected by USB|written for iPhone screens|device profile"
                    // The USB link to a phone timing out (seen on the 11 Pro Max: "Failed to read
                    // lockdown via appium-ios-remotexpc USBMUX path").
                    + "|lockdown|USBMUX|usbmux|remotexpc");
    private static final Pattern UI_CHANGED = Pattern.compile(
            "WaitTimeout|NoSuchElementException|never scrolled into view|is not on Home"
                    + "|Timed out after \\d+s waiting for|Locator health: not found"
                    // Page objects report a value they cannot find as "No <thing> on <screen>".
                    + "|AssertionError: No [^\\n]{1,80} on ");

    private final Map<Kind, List<String>> failures = new EnumMap<>(Kind.class);

    /** The kind of failure {@code error} most likely is. */
    public static Kind classify(Throwable error) {
        String trace = describe(error);
        if (ENVIRONMENT.matcher(trace).find()) {
            return Kind.ENVIRONMENT;
        }
        if (UI_CHANGED.matcher(trace).find()) {
            return Kind.UI_CHANGED;
        }
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof AssertionError) {
                return Kind.PRegistryBLE_BUG;
            }
        }
        return Kind.OTHER;
    }

    /** Class names and messages down the cause chain — what the patterns are matched against. */
    private static String describe(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = error; t != null && t.getCause() != t; t = t.getCause()) {
            text.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
        }
        return text.toString();
    }

    // ---------------------------------------------------------------- listener

    @Override
    public void onStart(ISuite suite) {
        writeAllureCategories();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        record(name(result), result.getThrowable());
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        record(name(result), result.getThrowable());
    }

    @Override
    public void onConfigurationFailure(ITestResult result) {
        record("setup " + result.getTestClass().getRealClass().getSimpleName() + "." + result.getName(),
                result.getThrowable());
    }

    @Override
    public void onFinish(ISuite suite) {
        if (failures.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("[iosautomation] ---------------- failure triage: " + suite.getName() + " ----------------");
        failures.forEach((kind, names) -> {
            lines.add("[iosautomation] " + kind.title + ": " + names.size());
            names.forEach(n -> lines.add("[iosautomation]    " + n));
        });
        lines.forEach(System.out::println);
        try {
            Path report = Paths.get("target", "failure-triage.txt");
            Files.createDirectories(report.getParent());
            Files.write(report, lines);
        } catch (IOException e) {
            System.err.println("[iosautomation] could not write target/failure-triage.txt: " + e);
        }
        failures.clear();
    }

    private synchronized void record(String name, Throwable error) {
        if (error == null) {
            return;
        }
        Kind kind = classify(error);
        String message = String.valueOf(error.getMessage()).replace('\n', ' ');
        String line = name + " — " + (message.length() > 160 ? message.substring(0, 160) + "…" : message);
        failures.computeIfAbsent(kind, k -> new ArrayList<>()).add(line);
        System.out.println("[iosautomation] " + kind.title + ": " + line);
    }

    private static String name(ITestResult result) {
        String description = result.getMethod().getDescription();
        return description == null || description.isBlank() ? result.getName() : description;
    }

    /**
     * Allure's "Categories" tab, grouped like {@link #classify}: categories
     * match on the failure's message and stack trace, first match wins.
     */
    private static void writeAllureCategories() {
        Path dir = Paths.get(System.getProperty("allure.results.directory", "allure-results"));
        String json = """
                [
                  {"name": "%s", "matchedStatuses": ["failed", "broken", "skipped"],
                   "traceRegex": "(?s).*(SessionNotCreatedException|ECONNREFUSED|socket hang up|Could not proxy command|xcodebuild|devicectl|Missing required configuration|No iPhone is connected|lockdown|USBMUX).*"},
                  {"name": "%s", "matchedStatuses": ["failed", "broken"],
                   "traceRegex": "(?s).*(WaitTimeout|NoSuchElementException|never scrolled into view|is not on Home|Locator health: not found|AssertionError: No [^\\n]{1,80} on ).*"},
                  {"name": "%s", "matchedStatuses": ["failed"],
                   "traceRegex": "(?s).*AssertionError.*"}
                ]
                """.formatted(Kind.ENVIRONMENT.title, Kind.UI_CHANGED.title, Kind.PRegistryBLE_BUG.title);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("categories.json"), json);
        } catch (IOException e) {
            System.err.println("[iosautomation] could not write Allure categories: " + e);
        }
    }
}
