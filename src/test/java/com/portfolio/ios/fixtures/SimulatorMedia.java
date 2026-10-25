package com.portfolio.ios.fixtures;

import com.portfolio.ios.core.Config;
import io.qameta.allure.Step;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Puts a test photo in the simulator's photo library, once.
 *
 * <p>The photo is one of the app's own meal-plan images
 * ({@code src/test/resources/media}). Whether it is already in the library is
 * read from the simulator's Photos database by its original file name, so
 * repeated runs do not pile up copies. Nothing else adds photos, so the test
 * photo stays the newest — the first cell of the picker.
 */
public final class SimulatorMedia {

    public static final String MEAL_PHOTO = "boiled-eggs-tomatoes.jpg";

    private SimulatorMedia() {
    }

    /** Adds {@link #MEAL_PHOTO} to the library unless it is already there. Simulator only. */
    @Step("Make sure the meal photo is in the simulator's library")
    public static void ensureMealPhoto() {
        if (Config.isReal()) {
            return;
        }
        if (isInLibrary(MEAL_PHOTO)) {
            return;
        }
        String added = AppStateResetter.simctl("addmedia", Config.udid(), resource(MEAL_PHOTO).toString());
        if (added == null) {
            throw new IllegalStateException("Could not add " + MEAL_PHOTO + " to the simulator's photo library");
        }
    }

    private static boolean isInLibrary(String fileName) {
        Path db = Config.simulatorDataDir().resolve("Media/PhotoData/Photos.sqlite");
        if (!Files.exists(db)) {
            return false;
        }
        try {
            Process query = new ProcessBuilder(List.of("sqlite3", "file:" + db + "?mode=ro",
                    "select count(*) from ZADDITIONALASSETATTRIBUTES where ZORIGINALFILENAME = '" + fileName + "';"))
                    .redirectErrorStream(true).start();
            String out = new String(query.getInputStream().readAllBytes()).trim();
            query.waitFor(10, TimeUnit.SECONDS);
            return out.matches("\\d+") && Integer.parseInt(out) > 0;
        } catch (Exception e) {
            // Unreadable database (schema change, locked): adding a copy is harmless.
            return false;
        }
    }

    private static Path resource(String name) {
        URL url = SimulatorMedia.class.getResource("/media/" + name);
        if (url == null) {
            throw new IllegalStateException("Test photo missing from src/test/resources/media: " + name);
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Bad resource path for " + name, e);
        }
    }
}
