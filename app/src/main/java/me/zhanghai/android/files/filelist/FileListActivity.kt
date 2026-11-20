/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 * Modified for mftlrs, 2026.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.Insets
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java8.nio.file.Path
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.toUserFriendlyString
import me.zhanghai.android.files.filelist.data.PickOptions
import me.zhanghai.android.files.filelist.ui.FileListRoot
import me.zhanghai.android.files.filelist.ui.ScrollPosition
import me.zhanghai.android.files.filelist.ui.isPersistentNavigation
import me.zhanghai.android.files.filelist.ui.scrollPosition
import me.zhanghai.android.files.filelist.ui.scrollTo
import me.zhanghai.android.files.navigation.NavigationItem
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.terminal.Terminal
import me.zhanghai.android.files.ui.theme.MftlrsTheme
import me.zhanghai.android.files.util.asFileName
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.createInstallPackageIntent
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.getParcelableSafe
import me.zhanghai.android.files.util.layoutInNavigation
import me.zhanghai.android.files.util.layoutInStatusBar
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.valueCompat

// The whole screen is FileListRoot in Compose, the logic and the screen
// state are in FileListViewModel. This activity executes the one-time events, keeps the states of
// Compose elements and is the listener of the navigation; permissions are in FileListPermissions.
class FileListActivity : AppActivity(), NavigationItem.Listener {
    private val viewModel by viewModels<FileListViewModel>()

    // Registers its launchers on creation.
    private val permissions = FileListPermissions(this) { viewModel }

    // Only the states of Compose elements are kept here: the scroll position of
    // FileListContent and the file name of FileListBottomBar.
    private val gridState = LazyGridState()
    private val createFileNameState = TextFieldState()
    private var isCreateFileNameFocusRequested by mutableStateOf(false)

    // System window insets for FileListScreen.
    private var windowInsets by mutableStateOf(Insets.NONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.file_list_title)
        setContent {
            MftlrsTheme {
                FileListRoot(
                    viewModel = viewModel,
                    windowInsets = windowInsets,
                    gridState = gridState,
                    createFileName = createFileNameState,
                    isCreateFileNameFocusRequested = isCreateFileNameFocusRequested,
                    onCreateFileNameFocused = { isCreateFileNameFocusRequested = false },
                    navigationListener = this@FileListActivity,
                    onTitleChange = { title = it },
                    onNavigationClick = { onNavigationClick() },
                    onNavigationOpenChange = { setNavigationOpen(it) },
                    onCopyPath = { copyPath(it) },
                    onOpenInNewTask = { startActivitySafe(createOpenInNewTaskIntent(it)) },
                    onPermissionDialogResult = { dialog, shouldRequest ->
                        permissions.onPermissionDialogResult(dialog, shouldRequest)
                    },
                )
            }
        }
        // Not on the ComposeView: Compose may replace
        // its insets listener.
        findViewById<View>(android.R.id.content).apply {
            // Was set by DrawerLayout and PersistentDrawerLayout with fitsSystemWindows: the screen
            // is drawn under the status bar.
            layoutInStatusBar = true
            // Was set by CoordinatorScrollingFrameLayout: the list is drawn under the navigation bar.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                layoutInNavigation = true
            }
            setOnApplyWindowInsetsListener { _, insets ->
                windowInsets = Insets.of(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
                insets
            }
        }

        savedInstanceState?.getString(STATE_CREATE_FILE_NAME)
            ?.let { createFileNameState.setTextAndPlaceCursorAtEnd(it) }
        savedInstanceState?.getParcelableSafe<ScrollPosition>(STATE_SCROLL_POSITION)
            ?.let { gridState.scrollTo(it) }
        viewModel.setFabExpanded(savedInstanceState?.getBoolean(STATE_FAB_EXPANDED) == true)
        setNavigationOpen(savedInstanceState?.getBoolean(STATE_NAVIGATION_OPEN) == true)

        // The start path and the pick mode are read from the intent by the view model.
        viewModel.start(intent)
        viewModel.pickOptionsLiveData.observe(this) { initCreateFileName(it) }
        lifecycleScope.launch {
            snapshotFlow { gridState.scrollPosition }.collect { viewModel.scrollPosition = it }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { onEvent(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        permissions.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val state = viewModel.uiState.value
        outState.putParcelable(STATE_SCROLL_POSITION, gridState.scrollPosition)
        outState.putString(STATE_CREATE_FILE_NAME, createFileNameState.text.toString())
        outState.putBoolean(STATE_FAB_EXPANDED, state.isFabExpanded)
        outState.putBoolean(STATE_NAVIGATION_OPEN, state.isNavigationOpen)
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (viewModel.onKeyShortcut(keyCode)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // One-time events of FileListViewModel.
    private fun onEvent(event: FileListEvent) {
        when (event) {
            is FileListEvent.ScrollTo -> gridState.scrollTo(event.position)
            is FileListEvent.ShowError -> showToast(event.text)
            is FileListEvent.ShowToast -> showToast(event.textRes)
            is FileListEvent.SetCreateFileName -> setCreateFileName(event.name)
            is FileListEvent.OpenInNewTask ->
                startActivitySafe(createOpenInNewTaskIntent(event.path))
            is FileListEvent.ViewFile ->
                startActivitySafe(createViewFileIntent(event.file, event.withChooser, event.files))
            is FileListEvent.InstallApk ->
                startActivitySafe(event.uri.createInstallPackageIntent())
            is FileListEvent.Share ->
                startActivitySafe(createShareIntent(event.paths, event.mimeTypes))
            is FileListEvent.CopyPath -> copyPath(event.path)
            is FileListEvent.OpenInTerminal -> Terminal.open(event.path.toFile().path, this)
            is FileListEvent.CreateShortcut ->
                ShortcutManagerCompat.requestPinShortcut(
                    this, createShortcutInfo(this, event.path, event.mimeType), null
                )
            is FileListEvent.Pick -> finishWithPickResult(event.paths, event.pickOptions)
            FileListEvent.Finish -> finish()
        }
    }

    private fun onNavigationClick() {
        if (resources.configuration.isPersistentNavigation) {
            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.putValue(
                !Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat
            )
        } else {
            setNavigationOpen(true)
        }
    }

    // The modal navigation drawer; the persistent one is FILE_LIST_PERSISTENT_DRAWER_OPEN.
    private fun setNavigationOpen(open: Boolean) {
        viewModel.setNavigationOpen(open && !resources.configuration.isPersistentNavigation)
    }

    private fun copyPath(path: Path) {
        clipboardManager.copyText(path.toUserFriendlyString(), this)
    }

    // The file name field is a Compose state kept here.
    private fun initCreateFileName(pickOptions: PickOptions?) {
        if (pickOptions?.mode != PickOptions.Mode.CREATE_FILE ||
            viewModel.isCreateFileNameEditInitialized) {
            return
        }
        setCreateFileName(pickOptions.fileName!!)
        isCreateFileNameFocusRequested = true
        viewModel.isCreateFileNameEditInitialized = true
    }

    // The base name is selected.
    private fun setCreateFileName(fileName: String) {
        createFileNameState.edit {
            replace(0, length, fileName)
            selection = TextRange(0, fileName.asFileName().baseName.length)
        }
    }

    // NavigationItem.Listener.
    override fun navigateTo(path: Path) {
        viewModel.navigateTo(path)
    }

    override val currentPath: Path
        get() = viewModel.currentPath

    override fun navigateToRoot(path: Path) {
        viewModel.navigateToRoot(path)
    }

    override fun launchIntent(intent: Intent) {
        startActivitySafe(intent)
    }

    override fun closeNavigationDrawer() {
        setNavigationOpen(false)
    }

    companion object {
        // States of Compose elements across recreation.
        private const val STATE_SCROLL_POSITION = "scrollPosition"
        private const val STATE_CREATE_FILE_NAME = "createFileName"
        private const val STATE_FAB_EXPANDED = "fabExpanded"
        private const val STATE_NAVIGATION_OPEN = "navigationOpen"

        fun createViewIntent(path: Path): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_VIEW)
                .apply { extraPath = path }
    }

    class OpenFileContract : ActivityResultContract<List<MimeType>, Path?>() {
        override fun createIntent(context: Context, input: List<MimeType>): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT)
                .setType(MimeType.ANY.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, input.map { it.value }.toTypedArray())

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class CreateFileContract : ActivityResultContract<Triple<MimeType, String?, Path?>, Path?>() {
        override fun createIntent(
            context: Context,
            input: Triple<MimeType, String?, Path?>
        ): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_CREATE_DOCUMENT)
                .setType(input.first.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .apply {
                    input.second?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    input.third?.let { extraPath = it }
                }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class OpenDirectoryContract : ActivityResultContract<Path?, Path?>() {
        override fun createIntent(context: Context, input: Path?): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .apply { input?.let { extraPath = it } }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }
}
