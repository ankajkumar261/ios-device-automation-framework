package com.portfolio.ios.core;

import com.portfolio.ios.core.ConnectedDevice.Device;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * How {@code auto} picks the phone to test, from devicectl's JSON — no phone
 * needed. Only an iPhone on the USB cable may be picked. The
 * sample carries a 64-bit unsigned number like real devicectl output does.
 */
@Story("Harness: configuration")
public class ConnectedDeviceTest {

    private static final String DEVICES = """
            {"result": {"devices": [
              {"capabilities": [{"featureIdentifier": 18446744071562067970}],
               "hardwareProperties": {"reality": "physical", "udid": "00008030-000A28101122802E"},
               "deviceProperties": {"name": "11 Pro Max Old", "osVersionNumber": "26.3.1 (a)"},
               "connectionProperties": {"transportType": "localNetwork", "pairingState": "paired"}},
              {"hardwareProperties": {"reality": "physical", "udid": "00008101-000E35101411001E"},
               "deviceProperties": {"name": "Jagwinder 12 pro", "osVersionNumber": "27.2"},
               "connectionProperties": {"transportType": "wired", "pairingState": "paired"}},
              {"hardwareProperties": {"reality": "simulated", "udid": "42E8A933-3202-4D32-BA33-0B61730170B4"},
               "deviceProperties": {"name": "iPhone 17 Pro", "osVersionNumber": "26.3"},
               "connectionProperties": {"pairingState": "paired"}}
            ]}}""";

    @Test(groups = {"config", "fast"})
    @Description("auto picks the one phone on the USB cable, with its UDID, name and iOS version")
    public void picksTheWiredPhone() {
        Device device = ConnectedDevice.pick(ConnectedDevice.parse(DEVICES));

        assertEquals(device.udid(), "00008101-000E35101411001E");
        assertEquals(device.name(), "Jagwinder 12 pro");
        assertEquals(device.iosVersion(), "27.2");
    }

    @Test(groups = {"config", "fast"})
    @Description("Simulators are never picked, and a Rapid Security Response letter is dropped from the version")
    public void readsOnlyPhysicalDevices() {
        List<Device> devices = ConnectedDevice.parse(DEVICES);

        assertEquals(devices.size(), 2, "the simulator is not a connected device");
        assertEquals(devices.get(0).iosVersion(), "26.3.1", "'26.3.1 (a)' -> '26.3.1'");
    }

    @Test(groups = {"config", "fast"})
    @Description("With no phone on a cable, the failure says so and names the phones seen over Wi-Fi")
    public void noCabledPhoneFailsNamingTheWifiOnes() {
        List<Device> wifiOnly = List.of(new Device("11 Pro Max Old", "00008030-000A28101122802E", "26.3.1", "localNetwork"));

        IllegalStateException e = expectThrows(IllegalStateException.class, () -> ConnectedDevice.pick(wifiOnly));
        assertTrue(e.getMessage().contains("USB") && e.getMessage().contains("11 Pro Max Old"),
                "failure must ask for a cable and name the Wi-Fi phone, was: " + e.getMessage());
    }

    @Test(groups = {"config", "fast"})
    @Description("With two iPhones on cables, auto tests the first by name; real.udid picks the other")
    public void twoCabledPhonesPickOneOrTheChosenOne() {
        List<Device> two = List.of(
                new Device("iPhone15 Pro", "00008130-000C4D4A0ADB803A", "27.0", "wired"),
                new Device("11 Pro Max Old", "00008030-000A28101122802E", "26.3.1", "wired"));

        assertEquals(ConnectedDevice.pick(two).udid(), "00008030-000A28101122802E", "first by name");
        assertEquals(ConnectedDevice.pick(two, "00008130-000C4D4A0ADB803A").udid(), "00008130-000C4D4A0ADB803A",
                "-Diosautomation.real.udid picks that phone");
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> ConnectedDevice.pick(two, "00008101-000E35101411001E"));
        assertTrue(e.getMessage().contains("not connected by USB") && e.getMessage().contains("11 Pro Max Old"),
                "a chosen phone that is not on a cable is reported, was: " + e.getMessage());
    }

    @Test(groups = {"config", "fast"})
    @Description("An iPad on a cable is never picked over an iPhone, and alone it is reported")
    public void iPadIsNeverPicked() {
        Device iPad = new Device("iPad 10th Gen", "00008101-0008383E1A11A01E", "26.2", "wired", "iPad");
        Device phone = new Device("11 Pro Max Old", "00008030-000A28101122802E", "26.3.1", "wired", "iPhone");

        assertEquals(ConnectedDevice.pick(List.of(iPad, phone)).udid(), phone.udid());
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> ConnectedDevice.pick(List.of(iPad)));
        assertTrue(e.getMessage().contains("iPhone"), "says an iPhone is needed, was: " + e.getMessage());
    }
}
