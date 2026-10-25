package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

/**
 * Steps 17-20 — height, weight, target weight, and goal pace.
 *
 * <p><b>The hard part.</b> Steps 17-19 are driven by the app's custom
 * {@code RulerScalePicker}, which is a SwiftUI {@code Canvas} painted with a
 * {@code DragGesture(minimumDistance: 0)}. It is not a {@code Slider}, not a
 * {@code PickerView}, and exposes no accessibility children, so none of the
 * standard XCUITest affordances apply — the only way to move it is a synthetic
 * drag, and the only way to know where the drag landed is to read the number
 * above the ruler and adjust.
 *
 * <p>Hence the closed-loop approach, implemented in {@link RulerControl}:
 * <ol>
 *   <li>read the current value from the large numeric readout above the ruler</li>
 *   <li>convert the remaining delta to points using the ruler's own geometry
 *       ({@code spacing} 8pt per tick, {@code step} units per tick)</li>
 *   <li>drag horizontally from a point on the ruler</li>
 *   <li>read the value again and repeat until it lands on target</li>
 * </ol>
 *
 * <p>This self-corrects against any inaccuracy in the geometry, the device's
 * safe-area insets, or a drag the OS partially swallowed — which matters,
 * because the ruler's mapping is authored in the app and can drift from this
 * assumptions without any compiler or test noticing.
 *
 * <p>Step 20 is a genuine SwiftUI {@code Slider} and is driven the conventional
 * way via {@code adjustToNormalizedSliderPosition}.
 */
public final class BodyMetricsPage extends StepPage {

    /** The closed-loop ruler driver described above, shared with Reset Goals. */
    private final RulerControl ruler;

    public BodyMetricsPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory, OnboardingStep.HEIGHT);
        this.ruler = new RulerControl(driver);
    }

    // ---------------------------------------------------------------- step 17

    @Step("Set height to {cm} cm")
    public void setHeightCm(double cm) {
        ruler.drive(cm, 1.0, "height");
        advanceFrom(OnboardingStep.HEIGHT, OnboardingStep.WEIGHT);
    }

    // ---------------------------------------------------------------- step 18

    @Step("Set weight to {kg} kg")
    public void setWeightKg(double kg) {
        ruler.drive(kg, 0.1, "weight");
        advanceFrom(OnboardingStep.WEIGHT, OnboardingStep.GOAL_WEIGHT);
    }

    // ---------------------------------------------------------------- step 19

    @Step("Set goal weight to {kg} kg")
    public void setGoalWeightKg(double kg) {
        ruler.drive(kg, 0.1, "goal weight");
        advanceFrom(OnboardingStep.GOAL_WEIGHT, OnboardingStep.WEIGHT_SPEED);
    }

    // ---------------------------------------------------------------- step 20

    /**
     * Step 20 — the goal pace, a real {@code Slider} bound to weekly weight
     * change. Takes a 0..1 position rather than a rate, because the slider's
     * range and unit depend on the KG/LB toggle and the caller's weight delta.
     */
    @Step("Set pace slider to {fraction}")
    public void setPace(double normalisedPosition) {
        Waits.visible(driver, Locators.headingContaining("How fast do you want to"), Waits.MEDIUM,
                "pace heading");
        WebElement slider = Waits.until(Waits.MEDIUM, "pace slider",
                () -> driver.findElement(Locators.byType("XCUIElementTypeSlider")));
        slider.sendKeys(String.valueOf(normalisedPosition));
        advanceFrom(OnboardingStep.WEIGHT_SPEED, OnboardingStep.ACTIVITY);
    }
}
