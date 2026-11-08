package com.focusguard.app

import android.app.admin.DeviceAdminReceiver

/**
 * Has no powers at all. While it is switched on, Android refuses to uninstall FocusGuard until it is
 * switched off again, which makes it harder to remove on a whim.
 */
class FocusAdminReceiver : DeviceAdminReceiver()
