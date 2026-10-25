package com.portfolio.ios.base;

import com.portfolio.ios.core.Config;
import org.openqa.selenium.SessionNotCreatedException;
import org.testng.SkipException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Real device only: stops a run from asking a phone for a session again and
 * again after it has refused once (locked, UI automation not allowed, cable
 * pulled). The first refusal fails its class as usual; every later class and
 * onboarding test is skipped with the same reason, so the run ends in seconds
 * instead of failing slowly class after class.
 *
 * <p>The refusal is also written to {@link #MARKER}; CI reads it to know the
 * iPhone could not be used and runs the suite on the simulator instead.
 */
final class DeviceAvailability {

    /** Present after a run in which the device refused a session. */
    static final Path MARKER = Paths.get("target", "device-unavailable.txt");

    private static volatile Throwable refusal;

    private DeviceAvailability() {
    }

    /** Throws a skip when the device already refused a session in this run. */
    static void skipIfRefused() {
        if (refusal != null) {
            throw new SkipException("Skipped: the iPhone could not be used in this run — " + refusal.getMessage());
        }
    }

    /** Remembers {@code error} when it means the device would not give a session at all. */
    static void recordIfRefused(Throwable error) {
        if (!Config.isReal() || refusal != null || !isSessionRefused(error)) {
            return;
        }
        refusal = error;
        try {
            Files.createDirectories(MARKER.getParent());
            Files.writeString(MARKER, String.valueOf(error.getMessage()) + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("[iosautomation] could not write " + MARKER + ": " + e.getMessage());
        }
        System.err.println("[iosautomation] the iPhone refused a session; the rest of this run is skipped: "
                + error.getMessage());
    }

    /** True for "no session at all" (refused, locked, not connected), not for a failure inside a session. */
    static boolean isSessionRefused(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SessionNotCreatedException
                    || String.valueOf(t.getMessage()).matches("(?s).*(No iPhone (or iPad )?is connected"
                            + "|is not connected by USB|written for iPhone screens).*")) {
                return true;
            }
        }
        return false;
    }
}
