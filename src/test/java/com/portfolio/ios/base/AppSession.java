package com.portfolio.ios.base;

import com.portfolio.ios.core.DriverFactory;
import io.appium.java_client.ios.IOSDriver;

/**
 * One Appium session shared by every regression class in a run, so
 * WebDriverAgent starts once instead of once per class. Closed at the end of
 * the suite ({@link AbstractAppTest#closeSharedSession()}), or before an
 * onboarding test that needs a session of its own.
 */
public final class AppSession {

    private static DriverFactory factory;

    private AppSession() {
    }

    public static synchronized DriverFactory factory() {
        return factory(true);
    }

    /**
     * The shared session, started if needed. On a real device
     * {@code freshInstall} decides whether starting it reinstalls the app;
     * an open session is returned as it is.
     */
    public static synchronized DriverFactory factory(boolean freshInstall) {
        if (factory == null) {
            factory = new DriverFactory(freshInstall);
        }
        return factory;
    }

    public static IOSDriver driver() {
        return factory().driver();
    }

    public static synchronized boolean isOpen() {
        return factory != null;
    }

    public static synchronized void quit() {
        if (factory != null) {
            factory.quit();
            factory = null;
        }
    }
}
