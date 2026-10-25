package com.portfolio.ios.core;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * XCUITest locator strategies under the names WDA actually accepts.
 *
 * <p>Appium Java client 10 no longer offers a public factory for
 * {@code -ios predicate string}: its {@code iOSNsPredicateString} builds a
 * locator named {@code iOSNsPredicate}, which the server normalises to
 * {@code nspredicate}, and XCUITest rejects that outright with
 * {@code invalid selector: Locator Strategy 'nspredicate' is not supported for
 * this session}. The same applies to class chains.
 *
 * <p>The base class's constructor is protected, which is exactly the seam needed:
 * subclassing it is the supported way to supply a custom locator name without
 * reimplementing {@link By}'s plumbing. The alternative — dropping to XPath
 * everywhere — would work, but predicates resolve against the accessibility
 * server's index and are markedly faster on the large SwiftUI trees in this
 * app, so it is not the trade this suite should make.
 */
public final class IosBy extends AppiumBy {

    /** The strategy string WDA requires. */
    public static final String PREDICATE = "-ios predicate string";
    public static final String CLASS_CHAIN = "-ios class chain";

    /**
     * The expression this locator wraps.
     *
     * <p>Kept locally because the base class does not expose it, and it is what
     * {@link #toString()} needs in order to be useful in a failure message.
     */
    private final String ownSelector;

    private IosBy(String locatorName, String selector) {
        super(locatorName, selector, null);
        this.ownSelector = selector;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overridden because {@code AppiumBy.toString()} is hardcoded to print
     * "AppiumBy.<methodName>", which for these subclasses renders as
     * {@code AppiumBy.null} — the class has no method of its own. Every wait
     * failure in this suite quotes the locator, so the default would put
     * "AppiumBy.null" in place of the one piece of information that matters
     * when a selector stops matching.
     */
    @Override
    public String toString() {
        return "IosBy[" + ownSelector + "]";
    }

    /**
     * An NSPredicate expression, evaluated on the device.
     *
     * <p>Callers are responsible for quoting any literal. Prefer
     * {@link com.portfolio.ios.pages.Locators}, which handles the escaping — an
     * unescaped apostrophe produces a predicate that parses as a syntax error
     * rather than as "no match", which is a much more confusing failure.
     */
    public static By predicate(String expression) {
        return new IosBy(PREDICATE, expression);
    }

    /**
     * A class-chain selector — for example a descendant search over
     * {@code XCUIElementTypeButton}, optionally indexed.
     */
    public static By classChain(String chain) {
        return new IosBy(CLASS_CHAIN, chain);
    }
}