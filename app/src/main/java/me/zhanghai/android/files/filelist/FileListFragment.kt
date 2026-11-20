/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 * Modified for mftlrs, 2026.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.Insets
import androidx.fragment.app.Fragment
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java8.nio.file.Path
import java8.nio.file.Paths
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.checkSelfPermissionCompat
import me.zhanghai.android.files.databinding.FileListFragmentBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.file.extension
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.FileSortOptions.By
import me.zhanghai.android.files.filelist.FileSortOptions.Order
import me.zhanghai.android.files.fileproperties.FilePropertiesDialogFragment
import me.zhanghai.android.files.navigation.BookmarkDirectories
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.navigation.NavigationItem
import me.zhanghai.android.files.navigation.NavigationItemListLiveData
import me.zhanghai.android.files.provider.archive.createArchiveRootPath
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.terminal.Terminal
import me.zhanghai.android.files.theme.MftlrsTheme
import me.zhanghai.android.files.util.DebouncedRunnable
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.addOnBackPressedCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.asFileName
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.checkSelfPermission
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.create
import me.zhanghai.android.files.util.createInstallPackageIntent
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent
import me.zhanghai.android.files.util.createSendStreamIntent
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.getParcelableSafe
import me.zhanghai.android.files.util.layoutInNavigation
import me.zhanghai.android.files.util.layoutInStatusBar
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.supportsExternalStorageManager
import me.zhanghai.android.files.util.takeIfNotEmpty
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.viewModels
import me.zhanghai.android.files.util.withChooser
import me.zhanghai.android.files.viewer.image.ImageViewerActivity

class FileListFragment : Fragment(), FileItemListener,
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
    private val argsPath by lazy { args.intent.extraPath }

    private val viewModel by viewModels { { FileListViewModel() } }

    // mftlrs: the whole screen with the navigation drawer is in Compose.
    private lateinit var binding: FileListFragmentBinding

    // mftlrs: NavigationFragment and DrawerLayout are FileListNavigation in Compose.
    private val navigationOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeNavigationDrawer()
        }
    }

    // mftlrs: the screen state is FileListViewModel.uiState; only the states of Compose elements
    // are kept here: the scroll position of FileListContent and the file name of FileListBottomBar.
    private val gridState = LazyGridState()
    private val createFileNameState = TextFieldState()
    private var isCreateFileNameFocusRequested by mutableStateOf(false)

    // mftlrs: system window insets for FileListScreen, which was done by fitsSystemWindows views.
    private var windowInsets by mutableStateOf(Insets.NONE)

    // mftlrs: the speed dial is FileListFab in Compose.
    private val fabOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            setFabExpanded(false)
        }
    }

    private val debouncedSearchRunnable = DebouncedRunnable(Handler(Looper.getMainLooper()), 1000) {
        if (!isResumed || !viewModel.isSearchViewExpanded) {
            return@DebouncedRunnable
        }
        val query = viewModel.searchViewQuery
        if (query.isEmpty()) {
            return@DebouncedRunnable
        }
        viewModel.search(query)
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
        addOnBackPressedCallback(
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    collapseSearchView()
                }
            }
                .also { callback ->
                    viewModel.searchViewExpandedLiveData.observe(viewLifecycleOwner) {
                        callback.isEnabled = it
                    }
                }
        )
        // mftlrs: was the back callback of the overlay toolbar action mode.
        addOnBackPressedCallback(
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    clearSelectedFiles()
                }
            }
                .also { callback ->
                    viewModel.selectedFilesLiveData.observe(viewLifecycleOwner) {
                        callback.isEnabled = it.isNotEmpty()
                    }
                }
        )
        // mftlrs: was SpeedDialViewOnBackPressedCallback.
        addOnBackPressedCallback(fabOnBackPressedCallback)
        setFabExpanded(savedInstanceState?.getBoolean(STATE_FAB_EXPANDED) == true)
        // mftlrs: was DrawerLayoutOnBackPressedCallback; added last so that it is called first.
        addOnBackPressedCallback(navigationOnBackPressedCallback)
        setNavigationOpen(savedInstanceState?.getBoolean(STATE_NAVIGATION_OPEN) == true)

        if (!viewModel.hasTrail) {
            var path = argsPath
            val intent = args.intent
            var pickOptions: PickOptions? = null
            when (val action = intent.action) {
                Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT,
                Intent.ACTION_CREATE_DOCUMENT -> {
                    val mode = if (action == Intent.ACTION_CREATE_DOCUMENT) {
                        PickOptions.Mode.CREATE_FILE
                    } else {
                        PickOptions.Mode.OPEN_FILE
                    }
                    val mimeType = intent.type?.asMimeTypeOrNull() ?: MimeType.ANY
                    val fileName = if (mode == PickOptions.Mode.CREATE_FILE) {
                        intent.getStringExtra(Intent.EXTRA_TITLE)?.asFileNameOrNull()?.value
                            ?: mimeType.extension?.let { "file.$it" } ?: "file"
                    } else {
                        null
                    }
                    val readOnly = action == Intent.ACTION_GET_CONTENT
                    val extraMimeTypes = if (mode == PickOptions.Mode.OPEN_FILE) {
                        intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
                            ?.mapNotNull { it.asMimeTypeOrNull() }?.takeIfNotEmpty()
                    } else {
                        null
                    }
                    val mimeTypes = extraMimeTypes ?: listOf(mimeType)
                    val localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)
                    val allowMultiple = mode != PickOptions.Mode.CREATE_FILE &&
                        intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                    pickOptions =
                        PickOptions(mode, fileName, readOnly, mimeTypes, localOnly, allowMultiple)
                }
                Intent.ACTION_OPEN_DOCUMENT_TREE -> {
                    val localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)
                    pickOptions = PickOptions(
                        PickOptions.Mode.OPEN_DIRECTORY, null, false, emptyList(), localOnly, false
                    )
                }
                ACTION_VIEW_DOWNLOADS ->
                    path = Paths.get(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        ).path
                    )
                else ->
                    if (path != null) {
                        val mimeType = intent.type?.asMimeTypeOrNull()
                        if (mimeType != null && path.isArchiveFile(mimeType)) {
                            path = path.createArchiveRootPath()
                        }
                    }
            }
            if (path == null) {
                path = Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
            }
            viewModel.resetTo(path)
            if (pickOptions != null) {
                viewModel.pickOptions = pickOptions
            }
        }
        // mftlrs: the observers of the screen state moved to FileListViewModel.uiState.
        viewModel.pickOptionsLiveData.observe(viewLifecycleOwner) { initCreateFileName(it) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { onEvent(it) }
            }
        }
    }

    // mftlrs: one-time events of FileListViewModel.
    private fun onEvent(event: FileListEvent) {
        when (event) {
            is FileListEvent.ScrollTo -> gridState.scrollTo(event.position)
            is FileListEvent.ShowError -> showToast(event.text)
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
                    onSearchExpand = { expandSearchView() },
                    onSearchCollapse = { collapseSearchView() },
                    onSearchQueryChange = { onSearchQueryChange(it) },
                    onSearchSubmit = { onSearchSubmit(it) },
                    onAction = { onTopBarAction(it) },
                    onSelectionClose = { clearSelectedFiles() },
                    onSelectionAction = { onSelectionAction(it) },
                )
            },
            breadcrumbs = {
                FileListBreadcrumbs(
                    data = viewModel.breadcrumbLiveData.observeAsState().value,
                    onNavigate = { navigateTo(it) },
                    onCopyPath = { copyPath(it) },
                    onOpenInNewTask = { openInNewTask(it) },
                )
            },
            content = { bottomInset ->
                FileListContent(
                    state = state.content,
                    gridState = gridState,
                    listener = this@FileListFragment,
                    bottomInset = bottomInset,
                    onRefresh = { refresh() },
                )
            },
            bottomBar = {
                FileListBottomBar(
                    state = state.bottomBar,
                    fileName = createFileNameState,
                    isFileNameFocusRequested = isCreateFileNameFocusRequested,
                    onFileNameFocused = { isCreateFileNameFocusRequested = false },
                    onClose = { onBottomBarClose() },
                    onAction = { onBottomBarAction(it) },
                )
            },
            fab = { bottomPadding, endPadding ->
                FileListFab(
                    isExpanded = state.isFabExpanded,
                    bottomPadding = bottomPadding,
                    endPadding = endPadding,
                    onExpandedChange = { setFabExpanded(it) },
                    onAction = { onFabAction(it) },
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

    // mftlrs: the search was a SearchView in the options menu; its state stays in the view model.
    private fun expandSearchView() {
        viewModel.isSearchViewExpanded = true
        debouncedSearchRunnable()
    }

    private fun collapseSearchView() {
        if (!viewModel.isSearchViewExpanded) {
            return
        }
        viewModel.isSearchViewExpanded = false
        viewModel.stopSearching()
    }

    private fun onSearchQueryChange(query: String) {
        viewModel.searchViewQuery = query
        debouncedSearchRunnable()
    }

    private fun onSearchSubmit(query: String) {
        debouncedSearchRunnable.cancel()
        viewModel.search(query)
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
        val isOpen = open && !resources.configuration.isPersistentNavigation
        viewModel.setNavigationOpen(isOpen)
        navigationOnBackPressedCallback.isEnabled = isOpen
    }

    // mftlrs: was onOptionsItemSelected().
    private fun onTopBarAction(action: FileListTopBarAction) {
        when (action) {
            FileListTopBarAction.VIEW_LIST -> viewModel.viewType = FileViewType.LIST
            FileListTopBarAction.VIEW_GRID -> viewModel.viewType = FileViewType.GRID
            FileListTopBarAction.SORT_BY_NAME -> viewModel.setSortBy(By.NAME)
            FileListTopBarAction.SORT_BY_TYPE -> viewModel.setSortBy(By.TYPE)
            FileListTopBarAction.SORT_BY_SIZE -> viewModel.setSortBy(By.SIZE)
            FileListTopBarAction.SORT_BY_LAST_MODIFIED -> viewModel.setSortBy(By.LAST_MODIFIED)
            FileListTopBarAction.SORT_ORDER_ASCENDING ->
                viewModel.setSortOrder(
                    if (viewModel.sortOptions.order != Order.ASCENDING) {
                        Order.ASCENDING
                    } else {
                        Order.DESCENDING
                    }
                )
            FileListTopBarAction.SORT_DIRECTORIES_FIRST ->
                viewModel.setSortDirectoriesFirst(!viewModel.sortOptions.isDirectoriesFirst)
            FileListTopBarAction.VIEW_SORT_PATH_SPECIFIC ->
                viewModel.isViewSortPathSpecific = !viewModel.isViewSortPathSpecific
            FileListTopBarAction.NEW_TASK -> newTask()
            FileListTopBarAction.NAVIGATE_UP -> navigateUp()
            FileListTopBarAction.NAVIGATE_TO -> showNavigateToPathDialog()
            FileListTopBarAction.REFRESH -> refresh()
            FileListTopBarAction.SELECT_ALL -> selectAllFiles()
            FileListTopBarAction.SHOW_HIDDEN_FILES ->
                setShowHiddenFiles(!Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat)
            FileListTopBarAction.SHARE -> share()
            FileListTopBarAction.COPY_PATH -> copyPath()
            FileListTopBarAction.OPEN_IN_TERMINAL -> openInTerminal()
            FileListTopBarAction.ADD_BOOKMARK -> addBookmark()
            FileListTopBarAction.CREATE_SHORTCUT -> createShortcut()
        }
    }

    fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val state = viewModel.uiState.value
        // mftlrs: alphabetic shortcut of menu/file_list_paste.xml; a disabled item did not react.
        val bottomBar = state.bottomBar
        if (bottomBar.isVisible && keyCode == KeyEvent.KEYCODE_V && bottomBar.isActionEnabled) {
            val action = bottomBar.action
            if (action == FileListBottomBarAction.PASTE || action == FileListBottomBarAction.EXTRACT_HERE) {
                onBottomBarAction(action)
                return true
            }
        }
        // mftlrs: alphabetic shortcuts of menu/file_list_select.xml and file_list_pick.xml.
        if (state.selectionBar.isVisible) {
            val selectionAction = state.selectionBar.actions.firstOrNull {
                when (keyCode) {
                    KeyEvent.KEYCODE_X -> it == FileListSelectionAction.CUT
                    KeyEvent.KEYCODE_C ->
                        it == FileListSelectionAction.COPY || it == FileListSelectionAction.COPY_EXTRACT
                    KeyEvent.KEYCODE_A -> it == FileListSelectionAction.SELECT_ALL
                    else -> false
                }
            }
            if (selectionAction != null) {
                onSelectionAction(selectionAction)
                return true
            }
        }
        // mftlrs: alphabetic shortcuts of the removed menu/file_list.xml.
        val action = when (keyCode) {
            KeyEvent.KEYCODE_L -> FileListTopBarAction.NAVIGATE_TO
            KeyEvent.KEYCODE_R -> FileListTopBarAction.REFRESH
            KeyEvent.KEYCODE_A ->
                if (state.topBar.isSelectAllVisible) FileListTopBarAction.SELECT_ALL else null
            KeyEvent.KEYCODE_H -> FileListTopBarAction.SHOW_HIDDEN_FILES
            else -> null
        } ?: return false
        onTopBarAction(action)
        return true
    }

    private fun navigateUp() {
        collapseSearchView()
        viewModel.navigateUp()
    }

    private fun showNavigateToPathDialog() {
        NavigateToPathDialogFragment.show(currentPath, this)
    }

    private fun newTask() {
        openInNewTask(currentPath)
    }

    private fun refresh() {
        viewModel.reload()
    }

    private fun setShowHiddenFiles(showHiddenFiles: Boolean) {
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.putValue(showHiddenFiles)
    }

    private fun share() {
        shareFile(currentPath, MimeType.DIRECTORY)
    }

    private fun copyPath() {
        copyPath(currentPath)
    }

    private fun openInTerminal() {
        val path = currentPath
        if (path.isLinuxPath) {
            Terminal.open(path.toFile().path, requireContext())
        } else {
            // TODO
        }
    }

    override fun navigateTo(path: Path) {
        collapseSearchView()
        viewModel.navigateTo(gridState.scrollPosition, path)
    }

    private fun copyPath(path: Path) {
        clipboardManager.copyText(path.toUserFriendlyString(), requireContext())
    }

    private fun openInNewTask(path: Path) {
        val intent = FileListActivity.createViewIntent(path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        startActivitySafe(intent)
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

    private fun pickFiles(files: FileItemSet) {
        pickPaths(files.mapTo(linkedSetOf()) { it.path })
    }

    private fun pickPaths(paths: LinkedHashSet<Path>) {
        val intent = Intent().apply {
            val pickOptions = viewModel.pickOptions!!
            if (paths.size == 1) {
                val path = paths.single()
                data = path.fileProviderUri
                extraPath = path
            } else {
                val mimeTypes = pickOptions.mimeTypes.map { it.value }
                val items = paths.map { ClipData.Item(it.fileProviderUri) }
                clipData = ClipData::class.create(null, mimeTypes, items)
                extraPathList = paths.toList()
            }
            var flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            if (!pickOptions.readOnly) {
                flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            if (pickOptions.mode == PickOptions.Mode.OPEN_DIRECTORY) {
                flags = flags or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            }
            addFlags(flags)
        }
        requireActivity().run {
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    // mftlrs: was onOverlayActionModeMenuItemClicked().
    private fun onSelectionAction(action: FileListSelectionAction) {
        val files = viewModel.selectedFiles
        when (action) {
            FileListSelectionAction.OPEN -> pickFiles(files)
            FileListSelectionAction.CREATE -> confirmReplaceFile(files.single())
            FileListSelectionAction.CUT -> cutFiles(files)
            FileListSelectionAction.COPY, FileListSelectionAction.COPY_EXTRACT -> copyFiles(files)
            FileListSelectionAction.DELETE -> confirmDeleteFiles(files)
            FileListSelectionAction.EXTRACT -> extractFiles(files)
            FileListSelectionAction.ARCHIVE -> showCreateArchiveDialog(files)
            FileListSelectionAction.SHARE -> shareFiles(files)
            FileListSelectionAction.SELECT_ALL -> selectAllFiles()
        }
    }

    private fun confirmReplaceFile(file: FileItem, setFileName: Boolean = true) {
        if (setFileName) {
            setCreateFileName(file.name)
        }
        ConfirmReplaceFileDialogFragment.show(file, this)
    }

    // mftlrs: was setText() and setSelection() on the EditText; the base name is selected.
    private fun setCreateFileName(fileName: String) {
        createFileNameState.edit {
            replace(0, length, fileName)
            selection = TextRange(0, fileName.asFileName().baseName.length)
        }
    }

    override fun replaceFile(file: FileItem) {
        pickFiles(fileItemSetOf(file))
    }

    private fun cutFiles(files: FileItemSet) {
        viewModel.addToPasteState(false, files)
        viewModel.selectFiles(files, false)
    }

    private fun copyFiles(files: FileItemSet) {
        viewModel.addToPasteState(true, files)
        viewModel.selectFiles(files, false)
    }

    private fun confirmDeleteFiles(files: FileItemSet) {
        ConfirmDeleteFilesDialogFragment.show(files, this)
    }

    override fun deleteFiles(files: FileItemSet) {
        FileJobService.delete(makePathListForJob(files), requireContext())
        viewModel.selectFiles(files, false)
    }

    private fun extractFiles(files: FileItemSet) {
        copyFiles(files.mapTo(fileItemSetOf()) { it.createDummyArchiveRoot() })
        viewModel.selectFiles(files, false)
    }

    private fun showCreateArchiveDialog(files: FileItemSet) {
        CreateArchiveDialogFragment.show(files, this)
    }

    override fun archive(
        files: FileItemSet,
        name: String,
        format: Int,
        filter: Int,
        password: String?
    ) {
        val archiveFile = viewModel.currentPath.resolve(name)
        FileJobService.archive(
            makePathListForJob(files), archiveFile, format, filter, password, requireContext()
        )
        viewModel.selectFiles(files, false)
    }

    private fun shareFiles(files: FileItemSet) {
        shareFiles(files.map { it.path }, files.map { it.mimeType })
        viewModel.selectFiles(files, false)
    }

    private fun selectAllFiles() {
        val pickOptions = viewModel.pickOptions
        val files = viewModel.uiState.value.content.files
            .filterTo(fileItemSetOf()) { isFileSelectable(it, pickOptions) }
        selectFiles(files, true)
    }

    private fun onBottomBarClose() {
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            requireActivity().finish()
        } else {
            // mftlrs: was finishing the bottom action mode; the bar hides by the paste state.
            viewModel.clearPasteState()
        }
    }

    private fun onBottomBarAction(action: FileListBottomBarAction) {
        when (action) {
            FileListBottomBarAction.OPEN -> pickPaths(linkedSetOf(viewModel.currentPath))
            FileListBottomBarAction.CREATE -> {
                val fileName = createFileNameState.text.toString()
                if (fileName.isEmpty()) {
                    showToast(R.string.file_list_create_file_name_error_empty)
                } else if (fileName.asFileNameOrNull() == null) {
                    showToast(R.string.file_list_create_file_name_error_invalid)
                } else {
                    val file = getFileWithName(fileName)
                    if (file != null) {
                        confirmReplaceFile(file, false)
                    } else {
                        val path = viewModel.currentPath.resolve(fileName)
                        pickPaths(linkedSetOf(path))
                    }
                }
            }
            FileListBottomBarAction.PASTE, FileListBottomBarAction.EXTRACT_HERE ->
                pasteFiles(currentPath)
        }
    }

    private fun setFabExpanded(expanded: Boolean) {
        viewModel.setFabExpanded(expanded)
        fabOnBackPressedCallback.isEnabled = expanded
    }

    // mftlrs: was the speed dial action listener with menu/file_list_speed_dial.xml.
    private fun onFabAction(action: FileListFabAction) {
        when (action) {
            FileListFabAction.CREATE_FILE -> showCreateFileDialog()
            FileListFabAction.CREATE_DIRECTORY -> showCreateDirectoryDialog()
        }
        setFabExpanded(false)
    }

    private fun pasteFiles(targetDirectory: Path) {
        val pasteState = viewModel.pasteState
        if (viewModel.pasteState.copy) {
            FileJobService.copy(
                makePathListForJob(pasteState.files), targetDirectory, requireContext()
            )
        } else {
            FileJobService.move(
                makePathListForJob(pasteState.files), targetDirectory, requireContext()
            )
        }
        viewModel.clearPasteState()
    }

    private fun makePathListForJob(files: FileItemSet): List<Path> =
        files.map { it.path }.sortedBy { it.toUri() }

    override fun clearSelectedFiles() {
        viewModel.clearSelectedFiles()
    }

    override fun selectFile(file: FileItem, selected: Boolean) {
        viewModel.selectFile(file, selected)
    }

    override fun selectFiles(files: FileItemSet, selected: Boolean) {
        viewModel.selectFiles(files, selected)
    }

    override fun openFile(file: FileItem) {
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            if (file.attributes.isDirectory) {
                navigateTo(file.path)
            } else {
                when (pickOptions.mode) {
                    PickOptions.Mode.OPEN_FILE -> pickFiles(fileItemSetOf(file))
                    PickOptions.Mode.CREATE_FILE -> confirmReplaceFile(file)
                    PickOptions.Mode.OPEN_DIRECTORY -> {}
                }
            }
            return
        }
        if (file.mimeType.isApk) {
            openApk(file)
            return
        }
        if (file.isListable) {
            navigateTo(file.listablePath)
            return
        }
        openFileWithIntent(file, false)
    }

    private fun openApk(file: FileItem) {
        if (!file.isListable) {
            installApk(file)
            return
        }
        when (Settings.OPEN_APK_DEFAULT_ACTION.valueCompat) {
            OpenApkDefaultAction.INSTALL -> installApk(file)
            OpenApkDefaultAction.VIEW -> viewApk(file)
            OpenApkDefaultAction.ASK -> OpenApkDialogFragment.show(file, this)
        }
    }

    override fun installApk(file: FileItem) {
        val path = file.path
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!path.isArchivePath) path.fileProviderUri else null
        } else {
            // PackageInstaller only supports file URI before N.
            if (path.isLinuxPath) Uri.fromFile(path.toFile()) else null
        }
        if (uri != null) {
            startActivitySafe(uri.createInstallPackageIntent())
        } else {
            FileJobService.installApk(path, requireContext())
        }
    }

    override fun viewApk(file: FileItem) {
        navigateTo(file.listablePath)
    }

    override fun openFileWith(file: FileItem) {
        openFileWithIntent(file, true)
    }

    private fun openFileWithIntent(file: FileItem, withChooser: Boolean) {
        val path = file.path
        val mimeType = file.mimeType
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, withChooser, requireContext())
        } else {
            val intent = path.fileProviderUri.createViewIntent(mimeType)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .apply {
                    extraPath = path
                    maybeAddImageViewerActivityExtras(this, path, mimeType)
                }
                .let {
                    if (withChooser) {
                        it.withChooser(
                            EditFileActivity::class.createIntent()
                                .putArgs(EditFileActivity.Args(path, mimeType)),
                            OpenFileAsDialogActivity::class.createIntent()
                                .putArgs(OpenFileAsDialogFragment.Args(path))
                        )
                    } else {
                        it
                    }
                }
            startActivitySafe(intent)
        }
    }

    private fun maybeAddImageViewerActivityExtras(intent: Intent, path: Path, mimeType: MimeType) {
        if (!mimeType.isImage) {
            return
        }
        var paths = mutableListOf<Path>()
        // We need the ordered list from our adapter instead of the list from FileListLiveData.
        for (file in viewModel.uiState.value.content.files) {
            val filePath = file.path
            if (file.mimeType.isImage || filePath == path) {
                paths.add(filePath)
            }
        }
        var position = paths.indexOf(path)
        if (position == -1) {
            return
        }
        // HACK: Don't send too many paths to avoid TransactionTooLargeException.
        if (paths.size > IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX) {
            val start = (position - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX / 2)
                .coerceIn(0, paths.size - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
            paths = paths.subList(start, start + IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
            position -= start
        }
        ImageViewerActivity.putExtras(intent, paths, position)
    }

    override fun cutFile(file: FileItem) {
        cutFiles(fileItemSetOf(file))
    }

    override fun copyFile(file: FileItem) {
        copyFiles(fileItemSetOf(file))
    }

    override fun confirmDeleteFile(file: FileItem) {
        confirmDeleteFiles(fileItemSetOf(file))
    }

    override fun showRenameFileDialog(file: FileItem) {
        RenameFileDialogFragment.show(file, this)
    }

    override fun hasFileWithName(name: String): Boolean = getFileWithName(name) != null

    private fun getFileWithName(name: String): FileItem? {
        val fileListData = viewModel.fileListStateful
        if (fileListData !is Success) {
            return null
        }
        return fileListData.value.find { it.name == name }
    }

    override fun renameFile(file: FileItem, newName: String) {
        FileJobService.rename(file.path, newName, requireContext())
        viewModel.selectFile(file, false)
    }

    override fun extractFile(file: FileItem) {
        copyFile(file.createDummyArchiveRoot())
    }

    override fun showCreateArchiveDialog(file: FileItem) {
        showCreateArchiveDialog(fileItemSetOf(file))
    }

    override fun shareFile(file: FileItem) {
        shareFile(file.path, file.mimeType)
    }

    private fun shareFile(path: Path, mimeType: MimeType) {
        shareFiles(listOf(path), listOf(mimeType))
    }

    private fun shareFiles(paths: List<Path>, mimeTypes: List<MimeType>) {
        val uris = paths.map { it.fileProviderUri }
        val intent = uris.createSendStreamIntent(mimeTypes)
            .withChooser()
        startActivitySafe(intent)
    }

    override fun copyPath(file: FileItem) {
        copyPath(file.path)
    }

    override fun addBookmark(file: FileItem) {
        addBookmark(file.path)
    }

    private fun addBookmark() {
        addBookmark(currentPath)
    }

    private fun addBookmark(path: Path) {
        BookmarkDirectories.add(BookmarkDirectory(null, path))
        showToast(R.string.file_add_bookmark_success)
    }

    override fun createShortcut(file: FileItem) {
        createShortcut(file.path, file.mimeType)
    }

    private fun createShortcut() {
        createShortcut(currentPath, MimeType.DIRECTORY)
    }

    private fun createShortcut(path: Path, mimeType: MimeType) {
        val context = requireContext()
        val isDirectory = mimeType == MimeType.DIRECTORY
        val shortcutInfo = ShortcutInfoCompat.Builder(context, path.toString())
            .setShortLabel(path.name)
            .setIntent(
                if (isDirectory) {
                    FileListActivity.createViewIntent(path)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                } else {
                    OpenFileActivity.createIntent(path, mimeType)
                }
            )
            .setIcon(
                IconCompat.createWithResource(
                    context, if (isDirectory) {
                        R.mipmap.directory_shortcut_icon
                    } else {
                        R.mipmap.file_shortcut_icon
                    }
                )
            )
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            showToast(R.string.shortcut_created)
        }
    }

    override fun showPropertiesDialog(file: FileItem) {
        FilePropertiesDialogFragment.show(file, this)
    }

    private fun showCreateFileDialog() {
        CreateFileDialogFragment.show(this)
    }

    override fun createFile(name: String) {
        val path = currentPath.resolve(name)
        FileJobService.create(path, false, requireContext())
    }

    private fun showCreateDirectoryDialog() {
        CreateDirectoryDialogFragment.show(this)
    }

    override fun createDirectory(name: String) {
        val path = currentPath.resolve(name)
        FileJobService.create(path, true, requireContext())
    }

    override val currentPath: Path
        get() = viewModel.currentPath

    override fun navigateToRoot(path: Path) {
        collapseSearchView()
        viewModel.resetTo(path)
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
            refresh()
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
            refresh()
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
            refresh()
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
        private const val ACTION_VIEW_DOWNLOADS =
            "me.zhanghai.android.files.intent.action.VIEW_DOWNLOADS"

        private const val IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX = 1000

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
