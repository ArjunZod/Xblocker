# ShieldX: Device Owner Provisioning Guide

## Overview
Android provides two primary deployment modes for ShieldX:
1. **Normal Protection Mode (Consumer Mode)**: Standard APK installation. Provides local VPN DNS filtering, SafeSearch, and local classification. Can be uninstalled or disconnected by any device user with access to settings.
2. **Locked Managed Device Mode (Device Owner / Android Enterprise)**: Enterprise-grade device ownership. Unlocks unbreakable uninstall protection, Always-on VPN with fail-closed lockdown, VPN configuration restriction (`DISALLOW_CONFIG_VPN`), application suspension, and Safe Mode blocking.

---

## Step-by-Step ADB Provisioning Instructions

### Prerequisites:
1. Enable **Developer Options** on your Android device (Go to *Settings > About Phone > Tap 'Build Number' 7 times*).
2. Enable **USB Debugging** in *Developer Options*.
3. Remove all existing Google / personal accounts from the device (Go to *Settings > Accounts > Remove Accounts*).
   > *Note*: Android security policy forbids assigning a Device Owner if user accounts exist. Once provisioned as Device Owner, accounts can be safely re-added.
4. Connect the Android phone to your PC via USB cable.

### Provisioning Command:
Open a terminal (PowerShell / Command Prompt / Terminal) on your PC and run:

```bash
adb shell dpm set-device-owner com.antigravity.shieldx/.device.ShieldXDeviceAdminReceiver
```

### Verification:
Upon successful execution, ADB will output:
```
Success: Device owner set to package ComponentInfo{com.antigravity.shieldx/com.antigravity.shieldx.device.ShieldXDeviceAdminReceiver}
```
Open ShieldX on your phone. The dashboard badge will update to **MANAGED DEVICE OWNER** in green.

---

## Capabilities Activated in Device Owner Mode

- **Always-on VPN Lockdown**: Configures Android OS to route 100% of network traffic through ShieldX. If ShieldX VPN is disconnected or terminated, Android OS denies all internet access (`lockdownEnabled = true`).
- **Uninstall Prevention**: `setUninstallBlocked(admin, "com.antigravity.shieldx", true)` disables the uninstall button in Android system settings.
- **VPN Config Protection**: `DISALLOW_CONFIG_VPN` prevents adding alternative VPNs, proxies, or modifying VPN parameters.
- **Prohibited Application Suspension**: Tor Browser and unauthorized bypass tools are frozen via `setPackagesSuspended`.
- **Safe Mode Blocking**: `DISALLOW_SAFE_BOOT` prevents rebooting into Safe Mode.
