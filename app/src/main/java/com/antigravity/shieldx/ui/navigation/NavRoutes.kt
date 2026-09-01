package com.antigravity.shieldx.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")
    object Assistant : Screen("assistant", "Assistant")
    object TarziControl : Screen("tarzi_control", "Control")
    object Music : Screen("music", "Music")
    object Protection : Screen("protection", "Protection")
    object Automations : Screen("automations", "Automations")
    object Policies : Screen("policies", "Policies")
    object Apps : Screen("apps", "Applications")
    object BlockedLogs : Screen("blocked_logs", "Blocked Activity")
    object Diagnostics : Screen("diagnostics", "Diagnostics")
    object DeviceOwnerSetup : Screen("do_setup", "Device Owner Guide")
}
