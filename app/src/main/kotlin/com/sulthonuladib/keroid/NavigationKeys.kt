package com.sulthonuladib.keroid

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object Connections : NavKey
@Serializable data object Settings : NavKey
@Serializable data object TerminalProbe : NavKey
@Serializable data object TerminalAppearanceSettings : NavKey
@Serializable data class TerminalThemePicker(val dark: Boolean) : NavKey
@Serializable data class MachineEditor(val machineId: String) : NavKey
@Serializable data class IdentityEditor(val identityId: String) : NavKey
@Serializable data class ProjectBrowser(val machineId: String) : NavKey
@Serializable data class SessionDetail(val machineId: String, val sessionId: String) : NavKey
