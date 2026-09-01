# ShieldX: Troubleshooting & Diagnostics Guide

## Common Operational Issues

### 1. `dpm set-device-owner` fails with "Not allowed to set the device owner because there are already some accounts on the device"
- **Cause**: Android OS requires no active Google / email accounts during initial Device Owner provisioning.
- **Solution**: Go to *Settings > Accounts* and remove all accounts temporarily. Re-run the ADB command, then re-add your accounts.

### 2. DNS Queries Not Resolving / Network Offline
- **Cause**: Upstream DNS timeout or VPN loopback interception failure.
- **Solution**: Open ShieldX, tap **Diagnostics**, verify that `ProtectionVpnService` is running. Check that Wi-Fi / Mobile Data is connected. In Managed Mode, if VPN crashes, Android lockdown denies networking until the VPN restarts.

### 3. Forgot Admin PIN
- **Default PIN**: `1234` (if never modified by administrator).
- **Recovery**: In Managed Device mode, admin policies can be reconfigured or updated via enterprise EMM / MDM console.
