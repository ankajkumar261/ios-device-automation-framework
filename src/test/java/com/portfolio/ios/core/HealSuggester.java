package com.portfolio.ios.core;

import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heal <b>suggestions</b> for a locator that found nothing: the elements on the
 * current screen that look most like what the test wanted, each with a
 * ready-to-use replacement locator — e.g. "'Log 2 items' not found; closest:
 * Button 'Log 3 items' (90%)".
 *
 * <p>Deliberately suggestion-only. Nothing is tapped in place of the missing
 * element: a near-match on a screen with several similar buttons can be the
 * wrong one, and a test that "heals" onto it would pass while the feature is
 * broken. The suggestions make the fix a one-line change in
 * {@code Locators} instead of an investigation.
 *
 * <p>Works on this suite's locators as they appear in failure messages
 * ({@code IosBy[type == '…' AND label == '…']}) and on
 * {@code accessibilityId} locators.
 */
public final class HealSuggester {

    /** One look-alike on screen. {@code score} is 0–100. */
    public record Suggestion(String type, String label, int score, String note, String locator) {
        @Override
        public String toString() {
            return shortType(type) + " '" + label + "' (" + score + "%" + (note.isEmpty() ? "" : ", " + note) + ")"
                    + " → try: " + locator;
        }
    }

    private static final Pattern ACCESSIBILITY_ID = Pattern.compile("accessibilityId: ([^\\]\\n]+)");
    private static final Pattern TYPE = Pattern.compile("type\\s*==\\s*'(XCUIElementType\\w+)'");
    private static final Pattern TEXT = Pattern.compile(
            "(?:label|name|value)\\s*(?:==|CONTAINS(?:\\[c\\])?|BEGINSWITH|ENDSWITH|LIKE)\\s*['\"`]((?:[^'\"`\\\\]|\\\\.)*)['\"`]");
    private static final int MIN_SCORE = 55;
    private static final int MAX_LABEL = 200;

    private record Element(String type, String label, String name, boolean visible) {
    }

    private HealSuggester() {
    }

    // ---------------------------------------------------------------- entry points

    /**
     * For a failed test: finds the locators quoted in {@code error}'s messages,
     * ranks look-alikes on the current screen, prints them and attaches them
     * to Allure. Never throws — a suggestion must not mask the failure.
     */
    public static void report(IOSDriver driver, Throwable error) {
        if (driver == null || error == null) {
            return;
        }
        try {
            List<String> selectors = selectorsIn(messages(error));
            if (selectors.isEmpty()) {
                return;
            }
            String source = driver.getPageSource();
            StringBuilder text = new StringBuilder();
            for (String selector : selectors) {
                List<Suggestion> suggestions = suggest(selector, source);
                text.append("Not found: ").append(selector).append('\n');
                if (suggestions.isEmpty()) {
                    text.append("  nothing similar on this screen — it may have moved to another screen or been removed\n");
                }
                suggestions.forEach(s -> text.append("  closest: ").append(s).append('\n'));
            }
            System.out.print(text.toString().lines().map(l -> "[iosautomation] heal suggestion: " + l + "\n")
                    .reduce("", String::concat));
            Allure.addAttachment("heal-suggestions", "text/plain", text.toString(), ".txt");
        } catch (RuntimeException e) {
            System.err.println("[iosautomation] heal suggestions unavailable: " + e);
        }
    }

    /** Look-alikes for a locator as it prints itself ({@code IosBy[…]}, accessibility id). */
    public static List<Suggestion> suggest(org.openqa.selenium.By locator, String pageSource) {
        List<String> selectors = selectorsIn(String.valueOf(locator));
        return selectors.isEmpty() ? List.of() : suggest(selectors.get(0), pageSource);
    }

    /** Top look-alikes (best first, at most 3) for {@code selector} in {@code pageSource}. */
    public static List<Suggestion> suggest(String selector, String pageSource) {
        String wantedType = match(TYPE, selector);
        List<String> wanted = texts(selector);
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Suggestion> ranked = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element element : elements(pageSource)) {
            String candidate = !element.label().isEmpty() ? element.label() : element.name();
            if (candidate.isEmpty() || candidate.length() > MAX_LABEL || !seen.add(element.type() + "|" + candidate)) {
                continue;
            }
            int score = 0;
            for (String want : wanted) {
                score = Math.max(score, Math.max(similarity(want, element.label()), similarity(want, element.name())));
            }
            boolean sameType = wantedType == null || wantedType.equals(element.type());
            if (sameType && score < 100) {
                score = Math.min(99, score + 5);
            }
            if (score < MIN_SCORE) {
                continue;
            }
            List<String> notes = new ArrayList<>();
            if (!sameType) {
                notes.add("a " + shortType(element.type()) + ", not a " + shortType(wantedType));
            }
            if (!element.visible()) {
                notes.add("reported hidden");
            }
            if (score == 100 && sameType && element.visible()) {
                notes.add("text matches — another condition of the locator failed");
            }
            ranked.add(new Suggestion(element.type(), candidate, score, String.join(", ", notes),
                    "type == '" + element.type() + "' AND label == '" + escape(candidate) + "'"));
        }
        ranked.sort(Comparator.comparingInt(Suggestion::score).reversed());
        return ranked.subList(0, Math.min(3, ranked.size()));
    }

    // ---------------------------------------------------------------- parsing

    private static String messages(Throwable error) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = error; t != null && t.getCause() != t; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }

    /** Selectors quoted in a message: the inside of {@code IosBy[…]}, and accessibility ids as predicates. */
    static List<String> selectorsIn(String message) {
        Set<String> found = new LinkedHashSet<>();
        for (int start = message.indexOf("IosBy["); start >= 0; start = message.indexOf("IosBy[", start + 1)) {
            String selector = bracketed(message, start + "IosBy[".length());
            if (selector != null) {
                found.add(selector);
            }
        }
        Matcher id = ACCESSIBILITY_ID.matcher(message);
        while (id.find()) {
            found.add("name == '" + escape(id.group(1).trim()) + "'");
        }
        return new ArrayList<>(found);
    }

    /**
     * The text from {@code from} up to the {@code ]} that closes the opening
     * bracket before it — counting nested brackets ({@code CONTAINS[c]},
     * class-chain filters) and skipping those inside quotes.
     */
    private static String bracketed(String text, int from) {
        int depth = 1;
        char quote = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']' && --depth == 0) {
                return text.substring(from, i);
            }
        }
        return null;
    }

    private static List<String> texts(String selector) {
        List<String> texts = new ArrayList<>();
        Matcher m = TEXT.matcher(selector);
        while (m.find()) {
            String text = m.group(1).replace("\\'", "'").replace("\\\\", "\\").replace("*", "").trim();
            if (!text.isEmpty() && !text.startsWith("XCUIElementType")) {
                texts.add(text);
            }
        }
        return texts;
    }

    private static String match(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static List<Element> elements(String pageSource) {
        List<Element> elements = new ArrayList<>();
        try {
            javax.xml.parsers.DocumentBuilderFactory xml = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            xml.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.NodeList nodes = xml.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(pageSource.getBytes(StandardCharsets.UTF_8)))
                    .getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element node = (org.w3c.dom.Element) nodes.item(i);
                elements.add(new Element(node.getTagName(), node.getAttribute("label"), node.getAttribute("name"),
                        "true".equals(node.getAttribute("visible"))));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the page source", e);
        }
        return elements;
    }

    // ---------------------------------------------------------------- similarity

    /** 0–100: the better of edit-distance ratio and shared-word overlap, ignoring case and curly quotes. */
    static int similarity(String wanted, String candidate) {
        String a = normalize(wanted);
        String b = normalize(candidate);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 100;
        }
        double edit = 1.0 - (double) levenshtein(a, b) / Math.max(a.length(), b.length());
        // Labels repeat words ("0 of 4 streaks active today, 0 of 4 …"): build sets that allow it.
        Set<String> wordsA = new java.util.HashSet<>(List.of(a.split(" ")));
        Set<String> wordsB = new java.util.HashSet<>(List.of(b.split(" ")));
        long shared = wordsA.stream().filter(wordsB::contains).count();
        double words = (double) shared / (wordsA.size() + wordsB.size() - shared);
        return (int) Math.round(99 * Math.max(edit, words));
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"').replaceAll("\\s+", " ").trim();
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String shortType(String type) {
        return type == null ? "element" : type.replace("XCUIElementType", "");
    }
}
