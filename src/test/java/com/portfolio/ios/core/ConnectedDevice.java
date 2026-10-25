package com.portfolio.ios.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The iPhone plugged into this Mac — the {@code auto} device
 * ({@code real.device=auto} or {@code -Diosautomation.device=auto}): whatever iPhone is
 * on the cable is the one tested, with its UDID, name and iOS version read
 * from {@code xcrun devicectl list devices}, so any model and iOS version works
 * without a profile. With several iPhones on cables, {@code -Diosautomation.real.udid}
 * picks one; otherwise the first by name is used and the choice is logged.
 * An iPad is never picked: the tests are written for iPhone screens.
 *
 * <p>Only a USB connection counts. A phone paired over Wi-Fi shows up in
 * devicectl too, but Appium reaches devices through usbmuxd, which only sees
 * cabled ones — a Wi-Fi-only phone would fail much later with an opaque
 * WebDriverAgent error, so it is reported here instead.
 */
public final class ConnectedDevice {

    /** One physical device as devicectl reports it; {@code type} is "iPhone" or "iPad". */
    public record Device(String name, String udid, String iosVersion, String transport, String type) {
        public Device(String name, String udid, String iosVersion, String transport) {
            this(name, udid, iosVersion, transport, "iPhone");
        }

        boolean wired() {
            return "wired".equals(transport);
        }

        boolean iPhone() {
            return !"iPad".equals(type);
        }
    }

    private static volatile Device detected;

    private ConnectedDevice() {
    }

    /** The single phone connected by USB; detected once per run. */
    public static Device detect() {
        Device device = detected;
        if (device == null) {
            synchronized (ConnectedDevice.class) {
                if (detected == null) {
                    detected = pick(parse(listDevicesJson()), ConfigReader.override("real.udid"));
                }
                device = detected;
            }
        }
        return device;
    }

    static Device pick(List<Device> devices) {
        return pick(devices, null);
    }

    /**
     * The iPhone on a USB cable: {@code preferredUdid} when given, else the only
     * one, else the first by name (logged, with how to choose another). Fails
     * saying what is connected when no iPhone is on a cable.
     */
    static Device pick(List<Device> devices, String preferredUdid) {
        List<Device> wired = devices.stream().filter(Device::wired).toList();
        if (preferredUdid != null && !preferredUdid.isBlank()) {
            return wired.stream().filter(d -> preferredUdid.equals(d.udid())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("The device " + preferredUdid
                            + " (real.udid) is not connected by USB. On USB: "
                            + (wired.isEmpty() ? "nothing" : describe(wired))));
        }
        List<Device> phones = wired.stream().filter(Device::iPhone)
                .sorted(java.util.Comparator.comparing(Device::name)).toList();
        if (phones.size() == 1) {
            return phones.get(0);
        }
        if (phones.size() > 1) {
            Device chosen = phones.get(0);
            System.err.println("[iosautomation] Several iPhones are connected by USB: " + describe(phones) + ". Testing "
                    + chosen.name() + "; choose another with -Diosautomation.real.udid=<udid> or -Diosautomation.device=<profile>.");
            return chosen;
        }
        if (!wired.isEmpty()) {
            throw new IllegalStateException("Only " + describe(wired) + " is connected by USB, and the tests are "
                    + "written for iPhone screens. Connect an iPhone with a cable.");
        }
        String wifiOnly = devices.stream().filter(d -> !d.wired())
                .map(d -> d.name() + " (" + d.transport() + ")").collect(Collectors.joining(", "));
        throw new IllegalStateException("No iPhone is connected by USB. Plug the phone in with a cable "
                + "and unlock it — Appium cannot reach a phone over Wi-Fi."
                + (wifiOnly.isEmpty() ? "" : " Seen without a cable: " + wifiOnly));
    }

    private static String describe(List<Device> devices) {
        return devices.stream().map(d -> d.name() + " " + d.udid()).collect(Collectors.joining(", "));
    }

    /** Paired physical devices from {@code devicectl list devices --json-output}. */
    static List<Device> parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject result = object(root, "result");
        List<Device> found = new ArrayList<>();
        if (!result.has("devices")) {
            return found;
        }
        for (JsonElement element : result.getAsJsonArray("devices")) {
            JsonObject device = element.getAsJsonObject();
            // Xcode 27 / iOS 27 omits `reality` for newer phones and deprecates
            // hardware/device/connectionProperties in favour of `properties`.
            String reality = first(device, "properties.hardware.reality", "hardwareProperties.reality");
            String pairing = first(device, "properties.connection.pairingState",
                    "connectionProperties.pairingState");
            if (!"paired".equals(pairing)) {
                continue;
            }
            if (reality != null && !"physical".equals(reality)) {
                continue;
            }
            String udid = first(device, "properties.hardware.udid", "hardwareProperties.udid");
            if (udid == null) {
                continue;
            }
            String name = first(device, "properties.state.name", "deviceProperties.name");
            String transport = first(device, "properties.connection.transportType",
                    "connectionProperties.transportType");
            String type = first(device, "properties.hardware.deviceType", "hardwareProperties.deviceType");
            String ios = first(device, "deviceProperties.osVersionNumber",
                    "properties.software.osVersionNumber.stringValue");
            found.add(new Device(name, udid,
                    iosVersion(String.valueOf(ios)), transport, type));
        }
        return found;
    }

    /** First non-null string at any of the dotted paths (e.g. "properties.hardware.udid"). */
    private static String first(JsonObject root, String... paths) {
        for (String path : paths) {
            JsonObject cur = root;
            String[] parts = path.split("\\.");
            String value = null;
            for (int i = 0; i < parts.length; i++) {
                JsonElement child = cur != null ? cur.get(parts[i]) : null;
                if (child == null) {
                    value = null;
                    break;
                }
                if (i == parts.length - 1) {
                    value = child.isJsonPrimitive() ? child.getAsString() : null;
                } else if (child.isJsonObject()) {
                    cur = child.getAsJsonObject();
                } else {
                    value = null;
                    break;
                }
            }
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement child = parent.get(key);
        return child != null && child.isJsonObject() ? child.getAsJsonObject() : new JsonObject();
    }

    private static String text(JsonObject parent, String key) {
        JsonElement child = parent.get(key);
        return child != null && child.isJsonPrimitive() ? child.getAsString() : null;
    }

    /** "26.3.1 (a)" → "26.3.1": a Rapid Security Response letter is not part of the version Appium wants. */
    static String iosVersion(String raw) {
        int space = raw.indexOf(' ');
        return (space < 0 ? raw : raw.substring(0, space)).trim();
    }

    private static String listDevicesJson() {
        Path out = null;
        try {
            out = Files.createTempFile("iosautomation-devices", ".json");
            Process process = new ProcessBuilder("xcrun", "devicectl", "list", "devices",
                    "--json-output", out.toString()).redirectErrorStream(true).start();
            String log = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("xcrun devicectl list devices failed: " + log.strip());
            }
            return Files.readString(out);
        } catch (IOException e) {
            throw new IllegalStateException("Could not run xcrun devicectl to find the connected phone", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while listing connected devices", e);
        } finally {
            if (out != null) {
                try {
                    Files.deleteIfExists(out);
                } catch (IOException ignored) {
                    // a temp file left behind is harmless
                }
            }
        }
    }
}
