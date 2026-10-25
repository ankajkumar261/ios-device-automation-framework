package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The AI Coach chat ({@code Features/AICoach/AICoachView.swift}), a
 * full-screen cover opened from Home's AI INSIGHT banner.
 *
 * <p>Layout, verified on the simulator: an "AI Coach" navigation title with
 * Close (left) and "Clear chat history" (right, only once there are
 * messages); one scroll view holding the local insight card, the welcome
 * card (empty chat only) and the messages; a row of prompt chips; the text
 * field and its send button. Messages are not grouped in the tree: each is
 * a plain StaticText, and every coach reply is preceded by an "AI COACH"
 * caption — which is how {@link #turns()} pairs a question with its reply.
 *
 * <p>While a reply is pending the chips are disabled; the typing dots have
 * no accessibility label.
 */
public final class CoachPage extends BasePage {

    public static final String TITLE = "AI Coach";
    public static final String WELCOME_TITLE = "Your AI Nutrition Coach";
    public static final String WELCOME_INTRO = "Ask me about your macros, food choices, hydration, or how you're "
            + "tracking against your goal. For full meal plans, head to the Meal Plans tab.";
    public static final String PLACEHOLDER = "Ask your AI coach...";
    /** Prompt chips, in the order the app lists them. */
    public static final List<String> PROMPTS = List.of("What should I eat?", "Am I on track?", "Protein ideas",
            "Snack ideas", "Weekly review", "Macro balance", "Motivate me");

    private static final String REPLY_CAPTION = "AI COACH";
    private static final String CLEAR_BUTTON = "Clear chat history";
    private static final String SEND_BUTTON_NAME = "arrow.up.circle.fill";
    /** The coach backend's reply time is seconds, but it is the production AI. */
    private static final Duration BACKEND_REPLY = Duration.ofSeconds(90);
    private static final Set<String> NON_CHIP_BUTTONS = Set.of(CLEAR_BUTTON, "Arrow Up Circle", "Close", "Try again");

    /** One exchange: what the user sent and the coach's reply. */
    public record Turn(String user, String reply) {
    }

    public CoachPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    public CoachPage waitUntilLoaded() {
        visible(Locators.staticText(TITLE), Waits.MEDIUM, "'AI Coach' title");
        visible(Locators.visibleTextField(), Waits.SHORT, "coach text field");
        return this;
    }

    // ---------------------------------------------------------------- reading

    public boolean showsWelcome() {
        return Waits.isPresent(driver, Locators.staticText(WELCOME_TITLE));
    }

    public boolean showsWelcomeIntro() {
        return Waits.isPresent(driver, Locators.staticText(WELCOME_INTRO));
    }

    public boolean hasClearButton() {
        return Waits.isPresent(driver, Locators.visibleButton(CLEAR_BUTTON));
    }

    /** What the empty text field shows: its placeholder. */
    public String fieldText() {
        return Waits.resilientGet(() -> visible(Locators.visibleTextField(), Waits.SHORT, "coach text field")
                .getAttribute("value"));
    }

    public boolean isSendEnabled() {
        return Waits.resilientGet(() -> visible(Locators.visibleButtonNamed(SEND_BUTTON_NAME), Waits.SHORT,
                "send button").isEnabled());
    }

    /** The banner the app shows once today's AI limit is used up. */
    public static final String DAILY_LIMIT = "Your daily limit has reached. Try again tomorrow.";

    /** Waits briefly for the daily-limit banner (the app checks the limit just after opening). */
    public boolean showsDailyLimit() {
        return isVisible(Locators.staticText(DAILY_LIMIT), Waits.SHORT);
    }

    public boolean isFieldEnabled() {
        return Waits.resilientGet(() -> visible(Locators.visibleTextField(), Waits.SHORT, "coach text field")
                .isEnabled());
    }

    public boolean hasTryAgain() {
        return Waits.isPresent(driver, Locators.visibleButton("Try again"));
    }

    /** True when every prompt chip is enabled — false while a reply is pending. */
    public boolean areChipsEnabled() {
        return Waits.resilientGet(() -> driver.findElement(Locators.visibleButton(PROMPTS.get(0))).isEnabled());
    }

    /** Labels of the prompt chips, in order. */
    public List<String> promptChips() {
        List<String> chips = new ArrayList<>();
        boolean inCoach = false;
        for (String[] element : elementsInOrder()) {
            if (!inCoach) {
                inCoach = "XCUIElementTypeStaticText".equals(element[0]) && TITLE.equals(element[1]);
            } else if ("XCUIElementTypeButton".equals(element[0]) && !NON_CHIP_BUTTONS.contains(element[1])) {
                chips.add(element[1]);
            }
        }
        return chips;
    }

    /**
     * The first text under the title: the local insight card's heading
     * ("Good Morning!", "Ready to Log?", …).
     */
    public String insightCardTitle() {
        List<String> texts = chatTexts();
        if (texts.isEmpty()) {
            throw new AssertionError("No insight card under the AI Coach title");
        }
        return texts.get(0);
    }

    /** Every exchange still in the tree, oldest first. */
    public List<Turn> turns() {
        List<String> texts = chatTexts();
        List<Turn> turns = new ArrayList<>();
        for (int i = 1; i + 1 < texts.size(); i++) {
            if (REPLY_CAPTION.equals(texts.get(i))) {
                turns.add(new Turn(texts.get(i - 1), texts.get(i + 1)));
            }
        }
        return turns;
    }

    public Turn lastTurn() {
        List<Turn> turns = turns();
        if (turns.isEmpty()) {
            throw new AssertionError("The chat has no reply yet; texts: " + chatTexts());
        }
        return turns.get(turns.size() - 1);
    }

    public int replyCount() {
        return (int) chatTexts().stream().filter(REPLY_CAPTION::equals).count();
    }

    /**
     * StaticTexts of the coach screen between its title and the prompt chips:
     * insight card, welcome card and messages. Home's texts stay in the tree
     * under the cover but come before the title, so they are never read.
     */
    private List<String> chatTexts() {
        List<String> texts = new ArrayList<>();
        boolean inCoach = false;
        for (String[] element : elementsInOrder()) {
            if (!inCoach) {
                inCoach = "XCUIElementTypeStaticText".equals(element[0]) && TITLE.equals(element[1]);
                continue;
            }
            if ("XCUIElementTypeButton".equals(element[0]) && PROMPTS.contains(element[1])) {
                break;
            }
            if ("XCUIElementTypeStaticText".equals(element[0])) {
                texts.add(element[1]);
            }
        }
        return texts;
    }

    // ---------------------------------------------------------------- sending

    @Step("Type '{text}'")
    public CoachPage type(String text) {
        typeInto(visible(Locators.visibleTextField(), Waits.SHORT, "coach text field"), text);
        return this;
    }

    @Step("Tap send")
    public CoachPage tapSend() {
        tap(Locators.visibleButtonNamed(SEND_BUTTON_NAME), "coach send");
        return this;
    }

    /**
     * Types and sends a message the app answers on the device (a local rule);
     * returns the new exchange.
     */
    @Step("Send '{text}' (answered on the device)")
    public Turn sendLocal(String text) {
        int before = replyCount();
        type(text).tapSend();
        return waitForReply(before, Waits.SHORT);
    }

    /** Types and sends a message that goes to the coach backend; returns the new exchange. */
    @Step("Ask the coach '{text}'")
    public Turn ask(String text) {
        int before = replyCount();
        type(text).tapSend();
        return waitForReply(before, BACKEND_REPLY);
    }

    /** Taps a prompt chip; returns the new exchange once the backend replies. */
    @Step("Tap the '{chip}' prompt")
    public Turn tapPrompt(String chip) {
        int before = replyCount();
        tap(Locators.visibleButton(chip), "prompt chip");
        return waitForReply(before, BACKEND_REPLY);
    }

    /**
     * Waits for one more "AI COACH" reply than {@code before}. An error banner
     * ("Try again") fails at once with its text instead of waiting out the
     * timeout.
     */
    private Turn waitForReply(int before, Duration timeout) {
        Waits.until(timeout, "a coach reply", () -> {
            if (Waits.isPresent(driver, Locators.visibleButton("Try again"))) {
                throw new AssertionError("The coach showed an error instead of a reply: " + visibleTextsInOrder());
            }
            return replyCount() > before ? Boolean.TRUE : null;
        });
        return lastTurn();
    }

    /** Waits until the prompt chips are enabled again (no reply pending). */
    public CoachPage waitUntilIdle() {
        Waits.untilTrue(BACKEND_REPLY, "prompt chips enabled again", this::areChipsEnabled);
        return this;
    }

    // ---------------------------------------------------------------- clearing

    @Step("Open 'Clear chat?'")
    public CoachPage openClearChat() {
        tap(Locators.visibleButton(CLEAR_BUTTON), "coach toolbar");
        visible(Locators.alertTitled("Clear chat?"), Waits.SHORT, "'Clear chat?' alert");
        return this;
    }

    /** Title and message of the open "Clear chat?" alert. */
    public List<String> clearChatAlertTexts() {
        List<String> texts = new ArrayList<>();
        for (WebElement text : driver.findElements(Locators.alertTexts())) {
            texts.add(Waits.resilientGet(() -> text.getAttribute("label")));
        }
        return texts;
    }

    @Step("Cancel clearing the chat")
    public CoachPage cancelClear() {
        tap(Locators.visibleButton("Cancel"), "'Clear chat?' alert");
        waitForGone(Locators.alertTitled("Clear chat?"), Waits.SHORT);
        return this;
    }

    @Step("Clear the chat")
    public CoachPage confirmClear() {
        tap(Locators.visibleButton("Clear"), "'Clear chat?' alert");
        waitForGone(Locators.alertTitled("Clear chat?"), Waits.SHORT);
        visible(Locators.staticText(WELCOME_TITLE), Waits.SHORT, "welcome card after clearing");
        return this;
    }

    /** Clears the chat if there is anything to clear, so a test starts from an empty chat. */
    public CoachPage clearIfAny() {
        if (hasClearButton()) {
            openClearChat().confirmClear();
        }
        return this;
    }

    // ---------------------------------------------------------------- leaving

    @Step("Close the AI Coach")
    public HomePage close() {
        hideKeyboard();
        tap(Locators.visibleButton("Close"), "AI Coach");
        waitForGone(Locators.staticText(TITLE), Waits.SHORT);
        return new HomePage(driver, factory).waitUntilLoaded();
    }
}
