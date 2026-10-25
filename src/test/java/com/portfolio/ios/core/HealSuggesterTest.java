package com.portfolio.ios.core;

import com.portfolio.ios.core.HealSuggester.Suggestion;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Heal suggestions from a page source — no device needed. */
@Story("Harness: failure triage")
public class HealSuggesterTest {

    private static final String SCREEN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <AppiumAUT>
              <XCUIElementTypeApplication type="XCUIElementTypeApplication" name="Cal Care" label="Cal Care" visible="true">
                <XCUIElementTypeButton type="XCUIElementTypeButton" name="0 of 4 streaks active today, 0 of 4 streaks active today" label="0 of 4 streaks active today, 0 of 4 streaks active today" visible="true"/>
                <XCUIElementTypeButton type="XCUIElementTypeButton" name="Log 3 items" label="Log 3 items" visible="true"/>
                <XCUIElementTypeButton type="XCUIElementTypeButton" name="Edit" label="Edit" visible="true"/>
                <XCUIElementTypeStaticText type="XCUIElementTypeStaticText" name="AI Coach" label="AI Coach" visible="false"/>
                <XCUIElementTypeStaticText type="XCUIElementTypeStaticText" name="Close" label="Close" visible="true"/>
                <XCUIElementTypeButton type="XCUIElementTypeButton" name="Don’t Allow" label="Don’t Allow" visible="true"/>
              </XCUIElementTypeApplication>
            </AppiumAUT>
            """;

    @Test(groups = {"config", "fast"})
    @Description("A reworded button is suggested with a ready replacement locator")
    public void rewordedButtonIsSuggested() {
        List<Suggestion> s = HealSuggester.suggest(
                "type == 'XCUIElementTypeButton' AND label == 'Log 2 items' AND visible == true", SCREEN);

        assertEquals(s.get(0).label(), "Log 3 items");
        assertTrue(s.get(0).score() >= 85, "close match, was " + s.get(0).score());
        assertEquals(s.get(0).locator(), "type == 'XCUIElementTypeButton' AND label == 'Log 3 items'");
    }

    @Test(groups = {"config", "fast"})
    @Description("Same text but hidden, or a different element type, is said so")
    public void hiddenAndWrongTypeAreExplained() {
        Suggestion hidden = HealSuggester.suggest(
                "type == 'XCUIElementTypeStaticText' AND label == 'AI Coach'", SCREEN).get(0);
        assertEquals(hidden.score(), 100);
        assertTrue(hidden.note().contains("hidden"), hidden.note());

        Suggestion wrongType = HealSuggester.suggest(
                "type == 'XCUIElementTypeButton' AND label == 'Close'", SCREEN).get(0);
        assertTrue(wrongType.note().contains("StaticText, not a Button"), wrongType.note());
    }

    @Test(groups = {"config", "fast"})
    @Description("Curly and straight apostrophes count as the same text")
    public void curlyApostrophesMatch() {
        Suggestion s = HealSuggester.suggest(
                "type == 'XCUIElementTypeButton' AND label == 'Don\\'t Allow'", SCREEN).get(0);
        assertEquals(s.label(), "Don’t Allow");
        assertEquals(s.score(), 100);
    }

    @Test(groups = {"config", "fast"})
    @Description("Nothing similar on screen gives no suggestion rather than a wild guess")
    public void unrelatedTextGivesNothing() {
        assertTrue(HealSuggester.suggest(
                "type == 'XCUIElementTypeButton' AND label == 'Choose from Library'", SCREEN).isEmpty());
    }

    @Test(groups = {"config", "fast"})
    @Description("Locators are read out of failure messages, including CONTAINS[c] and class chains")
    public void selectorsAreReadFromMessages() {
        String message = "Timed out after 8s waiting for: privacy consent checkbox "
                + "[IosBy[type == 'XCUIElementTypeButton' AND label CONTAINS[c] 'square']]; also "
                + "IosBy[**/XCUIElementTypeSwitch[`label == 'Water reminders'`]/XCUIElementTypeSwitch]";

        assertEquals(HealSuggester.selectorsIn(message), List.of(
                "type == 'XCUIElementTypeButton' AND label CONTAINS[c] 'square'",
                "**/XCUIElementTypeSwitch[`label == 'Water reminders'`]/XCUIElementTypeSwitch"));
    }
}
