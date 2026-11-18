package com.focusguard.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything that isn't about one session: language, permissions, protections, backup. */
class SettingsSheet : ProgSheet() {

    override val sheetTitle = R.string.settings_title

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val ctx = requireContext()
        try {
            ctx.contentResolver.openOutputStream(uri)?.use {
                it.write(Prefs(ctx).exportJson().toByteArray(Charsets.UTF_8))
            }
            ctx.toast(R.string.backup_saved)
        } catch (e: Exception) {
            ctx.toast(R.string.backup_failed)
        }
    }

    private val restoreBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val ctx = requireContext()
        try {
            val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
            if (text != null && Prefs(ctx).importJson(text)) {
                ctx.toast(R.string.restore_done)
                UsageNotificationService.sync(ctx)
                (activity as? MainActivity)?.recreate()
            } else {
                ctx.toast(R.string.restore_invalid)
            }
        } catch (e: Exception) {
            ctx.toast(R.string.backup_failed)
        }
    }

    private val askPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val ctx = context ?: return@registerForActivityResult
        Prefs(ctx).permNotification = granted
        if (!granted) ctx.toast(R.string.perm_notif_denied)
        UsageNotificationService.sync(ctx)
        rebuild()
    }

    private val askReminderNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val ctx = context ?: return@registerForActivityResult
        if (!granted) ctx.toast(R.string.perm_notif_denied)
        Reminders.scheduleAll(ctx)
    }

    /** Reminders are notifications, so Android 13+ needs the notification permission. */
    private fun askReminderPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askReminderNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun render(ctx: Context, col: LinearLayout) {
        val prefs = Prefs(ctx)

        // ---- permissions
        col.addView(Ui.section(ctx, getString(R.string.set_permissions)))
        val a11y = isAccessibilityServiceEnabled(ctx)
        val usage = Usage.hasAccess(ctx)
        val nl = isNotificationListenerEnabled(ctx)
        addCard(
            col,
            Ui.row(ctx, getString(R.string.perm_accessibility), getString(R.string.perm_accessibility_why),
                onOff(a11y)) { openAccessibilitySettings(ctx) },
            Ui.row(ctx, getString(R.string.perm_usage), getString(R.string.perm_usage_why),
                onOff(usage)) { Usage.openAccessSettings(ctx) },
            Ui.row(ctx, getString(R.string.perm_notif_access), getString(R.string.perm_notif_access_why),
                onOff(nl)) { openNotificationListenerSettings(ctx) }
        )

        // ---- permanent notification
        col.addView(Ui.section(ctx, getString(R.string.set_notification)))
        addCard(
            col,
            Ui.switchRow(ctx, getString(R.string.perm_notification), getString(R.string.perm_notification_sub),
                prefs.permNotification) { on -> setPermanent(ctx, on) }
        )

        // ---- study tools
        col.addView(Ui.section(ctx, getString(R.string.set_study)))
        val f = Features(ctx)
        val study = ArrayList<android.view.View>()
        study.add(Ui.switchRow(ctx, getString(R.string.feat_dashboard), getString(R.string.feat_dashboard_sub), f.dashboard) {
            f.dashboard = it
            (activity as? MainActivity)?.applyFeatures()
        })
        study.add(Ui.switchRow(ctx, getString(R.string.feat_water), getString(R.string.feat_water_sub), f.water) {
            f.water = it
            (activity as? MainActivity)?.applyFeatures()
            rebuild()
        })
        study.add(Ui.switchRow(ctx, getString(R.string.feat_goals), getString(R.string.feat_goals_sub), f.goals) {
            f.goals = it
            (activity as? MainActivity)?.applyFeatures()
            rebuild()
        })
        study.add(Ui.switchRow(ctx, getString(R.string.feat_tips), getString(R.string.feat_tips_sub), f.tips) {
            f.tips = it
            (activity as? MainActivity)?.applyFeatures()
            rebuild()
        })
        if (f.water) {
            val w = Water(ctx)
            study.add(Ui.row(ctx, getString(R.string.water_title), getString(R.string.water_settings_sub),
                getString(R.string.water_goal_short, w.goal)) {
                WaterSheet().show(parentFragmentManager, "water")
            })
        }
        if (f.goals || f.tips) {
            study.add(Ui.switchRow(ctx, getString(R.string.set_evening), getString(R.string.set_evening_sub), f.eveningOn) {
                f.eveningOn = it
                Reminders.scheduleAll(ctx)
                askReminderPermission(ctx)
                rebuild()
            })
            if (f.eveningOn) {
                study.add(Ui.row(ctx, getString(R.string.set_evening_time), null, formatTime(f.eveningMin)) {
                    StudyUi.pickTime(ctx, f.eveningMin) {
                        f.eveningMin = it
                        Reminders.scheduleAll(ctx)
                        rebuild()
                    }
                })
            }
        }
        if (f.goals && f.dashboard) {
            study.add(Ui.switchRow(ctx, getString(R.string.set_revision), getString(R.string.set_revision_sub), f.revisionOn) {
                f.revisionOn = it
                Reminders.scheduleAll(ctx)
                askReminderPermission(ctx)
                rebuild()
            })
            if (f.revisionOn) {
                study.add(Ui.row(ctx, getString(R.string.set_revision_time), null, formatTime(f.revisionMin)) {
                    StudyUi.pickTime(ctx, f.revisionMin) {
                        f.revisionMin = it
                        Reminders.scheduleAll(ctx)
                        rebuild()
                    }
                })
            }
        }
        study.add(Ui.row(ctx, getString(R.string.reliable_title), getString(R.string.reliable_sub), StudyUi.batteryStatus(ctx)) {
            StudyUi.openBatterySettings(ctx)
        })
        col.addView(Ui.cardOf(ctx, study), Ui.match())

        // ---- pause screen
        col.addView(Ui.section(ctx, getString(R.string.set_pause)))
        addCard(
            col,
            Ui.row(ctx, getString(R.string.pause_apps), getString(R.string.pause_apps_sub),
                prefs.pausePackages.size.toString()) {
                AppPickerSheet.newInstance(AppPickerSheet.MODE_PAUSE).show(parentFragmentManager, "apps")
            },
            Ui.row(ctx, getString(R.string.pause_length), null, getString(R.string.n_seconds, prefs.pauseSeconds)) {
                ctx.promptNumber(getString(R.string.pause_length), prefs.pauseSeconds, 3, 60, getString(R.string.unit_seconds)) {
                    prefs.pauseSeconds = it
                    rebuild()
                }
            },
            Ui.row(ctx, getString(R.string.pause_grace), getString(R.string.pause_grace_sub),
                formatMinutes(prefs.pauseGraceMin)) {
                ctx.promptNumber(getString(R.string.pause_grace), prefs.pauseGraceMin, 1, 60, getString(R.string.unit_min)) {
                    prefs.pauseGraceMin = it
                    rebuild()
                }
            }
        )

        // ---- strict mode
        col.addView(Ui.section(ctx, getString(R.string.set_strict)))
        addCard(
            col,
            Ui.switchRow(ctx, getString(R.string.strict_split), getString(R.string.strict_split_sub),
                prefs.blockSplitScreen) { on ->
                if (lockedToast(ctx)) {
                    rebuild()
                } else {
                    prefs.blockSplitScreen = on
                }
            },
            Ui.row(ctx, getString(R.string.strict_admin), getString(R.string.strict_admin_sub),
                onOff(isAdminActive(ctx))) { toggleAdmin(ctx) },
            Ui.switchRow(ctx, getString(R.string.emergency_enabled), getString(R.string.emergency_enabled_sub),
                prefs.emergencyEnabled) { on ->
                if (lockedToast(ctx)) {
                    rebuild()
                } else {
                    prefs.emergencyEnabled = on
                }
            },
            Ui.row(ctx, getString(R.string.emergency_cooldown), getString(R.string.emergency_cooldown_sub),
                getString(R.string.n_hours, prefs.emergencyCooldownHours)) {
                if (lockedToast(ctx)) return@row
                ctx.promptNumber(getString(R.string.emergency_cooldown), prefs.emergencyCooldownHours, 1, 168, getString(R.string.unit_hours)) {
                    prefs.emergencyCooldownHours = it
                    rebuild()
                }
            }
        )

        // ---- goal, language
        col.addView(Ui.section(ctx, getString(R.string.set_general)))
        addCard(
            col,
            Ui.row(ctx, getString(R.string.weekly_goal), getString(R.string.weekly_goal_sub),
                formatMinutes(prefs.weeklyGoalMin)) {
                ctx.promptDuration(getString(R.string.weekly_goal), prefs.weeklyGoalMin, 7 * 24 * 60) {
                    prefs.weeklyGoalMin = it
                    rebuild()
                }
            },
            Ui.row(ctx, getString(R.string.language), null, currentLanguageName()) { pickLanguage(ctx) },
            Ui.row(ctx, getString(R.string.survey_redo), getString(R.string.survey_redo_sub), null) {
                startActivity(Intent(ctx, SurveyActivity::class.java))
            }
        )

        // ---- backup
        col.addView(Ui.section(ctx, getString(R.string.set_backup)))
        addCard(
            col,
            Ui.row(ctx, getString(R.string.backup_export), getString(R.string.backup_export_sub), null) {
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                createBackup.launch("focusguard-backup-$stamp.json")
            },
            Ui.row(ctx, getString(R.string.backup_import), getString(R.string.backup_import_sub), null) {
                if (lockedToast(ctx)) return@row
                MaterialAlertDialogBuilder(ctx).setTitle(R.string.backup_import)
                    .setMessage(R.string.backup_import_warn)
                    .setPositiveButton(R.string.backup_choose_file) { _, _ ->
                        restoreBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )

        col.addView(Ui.note(ctx, getString(R.string.about_text, versionName(ctx))))
    }

    private fun onOff(on: Boolean): String = getString(if (on) R.string.state_on else R.string.state_off)

    private fun versionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun setPermanent(ctx: Context, on: Boolean) {
        if (!on) {
            Prefs(ctx).permNotification = false
            UsageNotificationService.sync(ctx)
            rebuild()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        Prefs(ctx).permNotification = true
        UsageNotificationService.sync(ctx)
        rebuild()
    }

    // ---- device admin (uninstall protection) ----

    private fun adminComponent(ctx: Context) = ComponentName(ctx, FocusAdminReceiver::class.java)

    private fun isAdminActive(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(adminComponent(ctx))
    } catch (e: Exception) {
        false
    }

    private fun toggleAdmin(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (isAdminActive(ctx)) {
            if (lockedToast(ctx)) return
            dpm.removeActiveAdmin(adminComponent(ctx))
            rebuild()
        } else {
            try {
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(ctx))
                        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.strict_admin_explain))
                )
            } catch (e: Exception) {
                ctx.toast(R.string.cant_open_settings)
            }
        }
    }

    // ---- language ----

    private val languageTags = arrayOf("", "en", "hi")

    private fun currentLanguageName(): String {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return when {
            tag.startsWith("hi") -> "हिन्दी"
            tag.startsWith("en") -> "English"
            else -> getString(R.string.language_system)
        }
    }

    private fun pickLanguage(ctx: Context) {
        val names = arrayOf(getString(R.string.language_system), "English", "हिन्दी")
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val checked = when {
            current.startsWith("hi") -> 2
            current.startsWith("en") -> 1
            else -> 0
        }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.language)
            .setSingleChoiceItems(names, checked) { d, which ->
                d.dismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTags[which]))
            }.show()
    }
}
