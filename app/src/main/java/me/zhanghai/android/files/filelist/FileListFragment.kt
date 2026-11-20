/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 * Modified for mftlrs, 2026.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextRange
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.Insets
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java8.nio.file.Path
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.checkSelfPermissionCompat
import me.zhanghai.android.files.databinding.FileListFragmentBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.fileproperties.FilePropertiesDialogFragment
import me.zhanghai.android.files.navigation.NavigationItem
import me.zhanghai.android.files.navigation.NavigationItemListLiveData
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.terminal.Terminal
import me.zhanghai.android.files.theme.MftlrsTheme
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.addOnBackPressedCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.asFileName
import me.zhanghai.android.files.util.checkSelfPermission
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.createInstallPackageIntent
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent
import me.zhanghai.android.files.util.getParcelableSafe
import me.zhanghai.android.files.util.layoutInNavigation
import me.zhanghai.android.files.util.layoutInStatusBar
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.supportsExternalStorageManager
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.viewModels

// mftlrs: the logic and the screen state are in FileListViewModel. This fragment hosts the Compose
// screen, executes the one-time events, and is the listener of the dialogs, the navigation and the
// permission requests.
class FileListFragment : Fragment(),
    ConfirmReplaceFileDialogFragment.Listener, OpenApkDialogFragment.Listener,
    ConfirmDeleteFilesDialogFragment.Listener, CreateArchiveDialogFragment.Listener,
    RenameFileDialogFragment.Listener, CreateFileDialogFragment.Listener,
    CreateDirectoryDialogFragment.Listener, NavigateToPathDialogFragment.Listener,
    NavigationItem.Listener, ShowRequestAllFilesAccessRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionInSettingsRationaleDialogFragment.Listener {
    private val requestAllFilesAccessLauncher = registerForActivityResult(
        RequestAllFilesAccessContract(), this::onRequestAllFilesAccessResult
    )
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), this::onRequestStoragePermissionResult
    )
    private val requestStoragePermissionInSettingsLauncher = registerForActivityResult(
        RequestPermissionInSettingsContract(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
        this::onRequestStoragePermissionInSettingsResult
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), this::onRequestNotificationPermissionResult
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionInSettingsLauncher = registerForActivityResult(
        RequestPermissionInSettingsContract(android.Manifest.permission.POST_NOTIFICATIONS),
        this::onRequestNotificationPermissionInSettingsResult
    )

    private val args by args<Args>()

    private val viewModel by viewModels { { FileListViewModel() } }

    // mftlrs: the whole screen with the navigation drawer is in Compose.
    private lateinit var binding: FileListFragmentBinding

    // mftlrs: only the states of Compose elements are kept here: the scroll position of
    // FileListContent and the file name of FileListBottomBar.
    private val gridState = LazyGridState()
    private val createFileNameState = TextFieldState()
    private var isCreateFileNameFocusRequested by mutableStateOf(false)

    // mftlrs: system window insets for FileListScreen, which was done by fitsSystemWindows views.
    private var windowInsets by mutableStateOf(Insets.NONE)

    // mftlrs: back callbacks, enabled by the screen state in onUiStateChanged().
    private val searchOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.collapseSearch()
        }
    }
    private val selectionOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.clearSelectedFiles()
        }
    }
    private val fabOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.setFabExpanded(false)
        }
    }
    private val navigationOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeNavigationDrawer()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FileListFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        requireActivity().setTitle(R.string.file_list_title)
        // mftlrs: no support action bar; the whole screen is FileListScreen in Compose.
        binding.screenLayout.apply {
            // Was set by DrawerLayout and PersistentDrawerLayout with fitsSystemWindows: the screen is drawn
            // under the status bar.
            layoutInStatusBar = true
            // Was set by CoordinatorScrollingFrameLayout: the list is drawn under the navigation bar.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                layoutInNavigation = true
            }
            // Not on the ComposeView: Compose may replace its insets listener.
            setOnApplyWindowInsetsListener { _, insets ->
                windowInsets = Insets.of(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
                insets
            }
        }
        binding.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MftlrsTheme {
                    // Collected only while started, so that the file list is not reloaded in
                    // background.
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    // Was set by onPickOptionsChanged().
                    val title = fileListTitle(state.topBar.pickOptions)
                    LaunchedEffect(title) { requireActivity().title = title }
                    FileListNavigation(
                        isOpen = state.isNavigationOpen,
                        isPersistentOpen = Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN
                            .observeAsState(Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat).value,
                        windowInsets = windowInsets,
                        items = NavigationItemListLiveData.observeAsState(NavigationItemListLiveData.valueCompat).value,
                        currentPath = viewModel.currentPathLiveData.observeAsState().value,
                        listener = this@FileListFragment,
                        onOpenChange = { setNavigationOpen(it) },
                    ) { screenInsets ->
                        Screen(state, screenInsets)
                    }
                }
            }
        }
        savedInstanceState?.getString(STATE_CREATE_FILE_NAME)
            ?.let { createFileNameState.setTextAndPlaceCursorAtEnd(it) }
        savedInstanceState?.getParcelableSafe<ScrollPosition>(STATE_SCROLL_POSITION)
            ?.let { gridState.scrollTo(it) }

        // mftlrs: exit confirmation; added first so that it is called last, and not when picking.
        addExitConfirmation().also { callback ->
            viewModel.pickOptionsLiveData.observe(viewLifecycleOwner) { callback.isEnabled = it == null }
        }
        val viewLifecycleOwner = viewLifecycleOwner
        addOnBackPressedCallback(
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    viewModel.navigateUp()
                }
            }
                .also { callback ->
                    viewModel.breadcrumbLiveData.observe(viewLifecycleOwner) {
                        callback.isEnabled = viewModel.canNavigateUpBreadcrumb
                    }
                }
        )
        // mftlrs: closing the search was done by the support action bar.
        addOnBackPressedCallback(searchOnBackPressedCallback)
        // mftlrs: was the back callback of the overlay toolbar action mode.
        addOnBackPressedCallback(selectionOnBackPressedCallback)
        // mftlrs: was SpeedDialViewOnBackPressedCallback.
        addOnBackPressedCallback(fabOnBackPressedCallback)
        viewModel.setFabExpanded(savedInstanceState?.getBoolean(STATE_FAB_EXPANDED) == true)
        // mftlrs: was DrawerLayoutOnBackPressedCallback; added last so that it is called first.
        addOnBackPressedCallback(navigationOnBackPressedCallback)
        setNavigationOpen(savedInstanceState?.getBoolean(STATE_NAVIGATION_OPEN) == true)

        // mftlrs: the start path and the pick mode are read from the intent by the view model.
        viewModel.start(args.intent)
        viewModel.pickOptionsLiveData.observe(viewLifecycleOwner) { initCreateFileName(it) }
        viewLifecycleOwner.lifecycleScope.launch {
            snapshotFlow { gridState.scrollPosition }.collect { viewModel.scrollPosition = it }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { onUiStateChanged(it) } }
                launch { viewModel.events.collect { onEvent(it) } }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (view != null) {
            val state = viewModel.uiState.value
            outState.putParcelable(STATE_SCROLL_POSITION, gridState.scrollPosition)
            // mftlrs: the EditText and the speed dial saved these themselves.
            outState.putString(STATE_CREATE_FILE_NAME, createFileNameState.text.toString())
            outState.putBoolean(STATE_FAB_EXPANDED, state.isFabExpanded)
            // mftlrs: DrawerLayout saved this itself.
            outState.putBoolean(STATE_NAVIGATION_OPEN, state.isNavigationOpen)
        }
    }

    // mftlrs: was in onActivityCreated(); FileListScreen in the navigation drawer of FileListNavigation.
    @Composable
    private fun Screen(state: FileListUiState, windowInsets: Insets) {
        FileListScreen(
            windowInsets = windowInsets,
            isBottomBarVisible = state.bottomBar.isVisible,
            topBar = {
                FileListTopBar(
                    state = state.topBar,
                    selectionBar = state.selectionBar,
                    onNavigationClick = { onNavigationClick() },
                    onSearchExpand = { viewModel.expandSearch() },
                    onSearchCollapse = { viewModel.collapseSearch() },
                    onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                    onSearchSubmit = { viewModel.onSearchSubmit(it) },
                    onAction = { viewModel.onTopBarAction(it) },
                    onSelectionClose = { viewModel.clearSelectedFiles() },
                    onSelectionAction = { viewModel.onSelectionAction(it) },
                )
            },
            breadcrumbs = {
                FileListBreadcrumbs(
                    data = viewModel.breadcrumbLiveData.observeAsState().value,
                    onNavigate = { viewModel.navigateTo(it) },
                    onCopyPath = { copyPath(it) },
                    onOpenInNewTask = { startActivitySafe(createOpenInNewTaskIntent(it)) },
                )
            },
            content = { bottomInset ->
                FileListContent(
                    state = state.content,
                    gridState = gridState,
                    listener = viewModel,
                    bottomInset = bottomInset,
                    onRefresh = { viewModel.reload() },
                )
            },
            bottomBar = {
                FileListBottomBar(
                    state = state.bottomBar,
                    fileName = createFileNameState,
                    isFileNameFocusRequested = isCreateFileNameFocusRequested,
                    onFileNameFocused = { isCreateFileNameFocusRequested = false },
                    onClose = { viewModel.onBottomBarClose() },
                    onAction = { viewModel.onBottomBarAction(it, createFileNameState.text.toString()) },
                )
            },
            fab = { bottomPadding, endPadding ->
                FileListFab(
                    isExpanded = state.isFabExpanded,
                    bottomPadding = bottomPadding,
                    endPadding = endPadding,
                    onExpandedChange = { viewModel.setFabExpanded(it) },
                    onAction = { viewModel.onFabAction(it) },
                )
            },
        )
    }

    override fun onResume() {
        super.onResume()

        if (!viewModel.isNotificationPermissionRequested) {
            ensureStorageAccess()
        }
        if (!viewModel.isStorageAccessRequested) {
            ensureNotificationPermission()
        }
    }

    fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean = viewModel.onKeyShortcut(keyCode)

    private fun onUiStateChanged(state: FileListUiState) {
        searchOnBackPressedCallback.isEnabled = state.topBar.isSearchExpanded
        selectionOnBackPressedCallback.isEnabled = state.selectionBar.isVisible
        fabOnBackPressedCallback.isEnabled = state.isFabExpanded
        navigationOnBackPressedCallback.isEnabled = state.isNavigationOpen
    }

    // mftlrs: one-time events of FileListViewModel.
    private fun onEvent(event: FileListEvent) {
        when (event) {
            is FileListEvent.ScrollTo -> gridState.scrollTo(event.position)
            is FileListEvent.ShowError -> showToast(event.text)
            is FileListEvent.ShowToast -> showToast(event.textRes)
            is FileListEvent.ShowDialog -> showDialog(event.dialog)
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
            is FileListEvent.OpenInTerminal ->
                Terminal.open(event.path.toFile().path, requireContext())
            is FileListEvent.CreateShortcut -> {
                val context = requireContext()
                ShortcutManagerCompat.requestPinShortcut(
                    context, createShortcutInfo(context, event.path, event.mimeType), null
                )
            }
            is FileListEvent.Pick ->
                requireActivity().finishWithPickResult(event.paths, event.pickOptions)
            FileListEvent.Finish -> requireActivity().finish()
        }
    }

    private fun showDialog(dialog: FileListDialog) {
        when (dialog) {
            is FileListDialog.NavigateToPath -> NavigateToPathDialogFragment.show(dialog.path, this)
            is FileListDialog.ConfirmReplaceFile ->
                ConfirmReplaceFileDialogFragment.show(dialog.file, this)
            is FileListDialog.ConfirmDeleteFiles ->
                ConfirmDeleteFilesDialogFragment.show(dialog.files, this)
            is FileListDialog.CreateArchive -> CreateArchiveDialogFragment.show(dialog.files, this)
            is FileListDialog.RenameFile -> RenameFileDialogFragment.show(dialog.file, this)
            is FileListDialog.OpenApk -> OpenApkDialogFragment.show(dialog.file, this)
            is FileListDialog.Properties -> FilePropertiesDialogFragment.show(dialog.file, this)
            FileListDialog.CreateFile -> CreateFileDialogFragment.show(this)
            FileListDialog.CreateDirectory -> CreateDirectoryDialogFragment.show(this)
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

    // mftlrs: the modal navigation drawer; the persistent one is FILE_LIST_PERSISTENT_DRAWER_OPEN.
    private fun setNavigationOpen(open: Boolean) {
        viewModel.setNavigationOpen(open && !resources.configuration.isPersistentNavigation)
    }

    private fun copyPath(path: Path) {
        clipboardManager.copyText(path.toUserFriendlyString(), requireContext())
    }

    // mftlrs: was in updateBottomToolbar(); the file name field is a Compose state kept here.
    private fun initCreateFileName(pickOptions: PickOptions?) {
        if (pickOptions?.mode != PickOptions.Mode.CREATE_FILE ||
            viewModel.isCreateFileNameEditInitialized) {
            return
        }
        setCreateFileName(pickOptions.fileName!!)
        isCreateFileNameFocusRequested = true
        viewModel.isCreateFileNameEditInitialized = true
    }

    // mftlrs: was setText() and setSelection() on the EditText; the base name is selected.
    private fun setCreateFileName(fileName: String) {
        createFileNameState.edit {
            replace(0, length, fileName)
            selection = TextRange(0, fileName.asFileName().baseName.length)
        }
    }

    // mftlrs: results of the dialogs go to the view model.

    override fun replaceFile(file: FileItem) {
        viewModel.replaceFile(file)
    }

    override fun deleteFiles(files: FileItemSet) {
        viewModel.deleteFiles(files)
    }

    override fun archive(
        files: FileItemSet,
        name: String,
        format: Int,
        filter: Int,
        password: String?
    ) {
        viewModel.archive(files, name, format, filter, password)
    }

    override fun installApk(file: FileItem) {
        viewModel.installApk(file)
    }

    override fun viewApk(file: FileItem) {
        viewModel.viewApk(file)
    }

    override fun hasFileWithName(name: String): Boolean = viewModel.getFileWithName(name) != null

    override fun renameFile(file: FileItem, newName: String) {
        viewModel.renameFile(file, newName)
    }

    override fun createFile(name: String) {
        viewModel.createFile(name)
    }

    override fun createDirectory(name: String) {
        viewModel.createDirectory(name)
    }

    // NavigateToPathDialogFragment.Listener and NavigationItem.Listener.
    override fun navigateTo(path: Path) {
        viewModel.navigateTo(path)
    }

    override val currentPath: Path
        get() = viewModel.currentPath

    override fun navigateToRoot(path: Path) {
        viewModel.navigateToRoot(path)
    }

    // mftlrs: was in NavigationFragment.
    override fun launchIntent(intent: Intent) {
        startActivitySafe(intent)
    }

    override fun closeNavigationDrawer() {
        setNavigationOpen(false)
    }

    private fun ensureStorageAccess() {
        if (viewModel.isStorageAccessRequested) {
            return
        }
        if (Environment::class.supportsExternalStorageManager()) {
            if (!Environment.isExternalStorageManager()) {
                ShowRequestAllFilesAccessRationaleDialogFragment.show(this)
                viewModel.isStorageAccessRequested = true
            }
        } else if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (shouldShowRequestPermissionRationale(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )) {
                ShowRequestStoragePermissionRationaleDialogFragment.show(this)
            } else {
                requestStoragePermission()
            }
            viewModel.isStorageAccessRequested = true
        }
    }

    override fun onShowRequestAllFilesAccessRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestAllFilesAccess()
        } else {
            viewModel.isStorageAccessRequested = false
            // This isn't an onActivityResult() callback so it's not delivered before calling
            // onResume(), and we need to do this manually.
            ensureNotificationPermission()
        }
    }

    private fun requestAllFilesAccess() {
        requestAllFilesAccessLauncher.launch(Unit)
    }

    private fun onRequestAllFilesAccessResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            viewModel.reload()
        }
    }

    override fun onShowRequestStoragePermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermission()
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun requestStoragePermission() {
        requestStoragePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun onRequestStoragePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isStorageAccessRequested = false
            viewModel.reload()
        } else if (shouldShowRequestPermissionRationale(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )) {
            ShowRequestStoragePermissionRationaleDialogFragment.show(this)
        } else {
            ShowRequestStoragePermissionInSettingsRationaleDialogFragment.show(this)
        }
    }

    override fun onShowRequestStoragePermissionInSettingsRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermissionInSettings()
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun requestStoragePermissionInSettings() {
        requestStoragePermissionInSettingsLauncher.launch(Unit)
    }

    private fun onRequestStoragePermissionInSettingsResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            viewModel.reload()
        }
    }

    private fun ensureNotificationPermission() {
        if (viewModel.isNotificationPermissionRequested) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )) {
                    ShowRequestNotificationPermissionRationaleDialogFragment.show(this)
                } else {
                    requestNotificationPermission()
                }
                viewModel.isNotificationPermissionRequested = true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onShowRequestNotificationPermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestNotificationPermission()
        } else {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
        } else if (shouldShowRequestPermissionRationale(
            android.Manifest.permission.POST_NOTIFICATIONS
        )) {
            ShowRequestNotificationPermissionRationaleDialogFragment.show(this)
        } else {
            ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.show(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onShowRequestNotificationPermissionInSettingsRationaleResult(
        shouldRequest: Boolean
    ) {
        if (shouldRequest) {
            requestNotificationPermissionInSettings()
        } else {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermissionInSettings() {
        requestNotificationPermissionInSettingsLauncher.launch(Unit)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionInSettingsResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    companion object {
        // mftlrs: scroll position of the Compose list across recreation.
        private const val STATE_SCROLL_POSITION = "scrollPosition"
        private const val STATE_CREATE_FILE_NAME = "createFileName"
        private const val STATE_FAB_EXPANDED = "fabExpanded"
        private const val STATE_NAVIGATION_OPEN = "navigationOpen"
    }

    private class RequestAllFilesAccessContract : ActivityResultContract<Unit, Boolean>() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun createIntent(context: Context, input: Unit): Intent =
            Environment::class.createManageAppAllFilesAccessPermissionIntent(context.packageName)

        @RequiresApi(Build.VERSION_CODES.R)
        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            Environment.isExternalStorageManager()
    }

    private class RequestPermissionInSettingsContract(private val permissionName: String)
        : ActivityResultContract<Unit, Boolean>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            application.checkSelfPermissionCompat(permissionName) ==
                PackageManager.PERMISSION_GRANTED
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs
}
