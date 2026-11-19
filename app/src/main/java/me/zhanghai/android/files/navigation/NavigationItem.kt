/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 * Modified for mftlrs, 2026.
 */

package me.zhanghai.android.files.navigation

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import java8.nio.file.Path

abstract class NavigationItem {
    abstract val id: Long

    // mftlrs: public for painterResource() in Compose (FileListNavigation); was getIcon(context).
    @get:DrawableRes
    abstract val iconRes: Int?

    abstract fun getTitle(context: Context): String

    open fun getSubtitle(context: Context): String? = null

    open fun isChecked(listener: Listener): Boolean = false

    abstract fun onClick(listener: Listener)

    open fun onLongClick(listener: Listener): Boolean = false

    interface Listener {
        val currentPath: Path
        fun navigateTo(path: Path)
        fun navigateToRoot(path: Path)
        fun launchIntent(intent: Intent)
        fun closeNavigationDrawer()
    }
}
