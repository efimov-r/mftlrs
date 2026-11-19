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
import android.text.TextUtils
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
import androidx.core.view.GravityCompat
import androidx.core.view.updatePaddingRelative
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextRange
import java8.nio.file.Path
import java8.nio.file.Paths
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.checkSelfPermissionCompat
import me.zhanghai.android.files.databinding.FileListFragmentAppBarIncludeBinding
import me.zhanghai.android.files.databinding.FileListFragmentBinding
import me.zhanghai.android.files.databinding.FileListFragmentBottomBarIncludeBinding
import me.zhanghai.android.files.databinding.FileListFragmentContentIncludeBinding
import me.zhanghai.android.files.databinding.FileListFragmentIncludeBinding
import me.zhanghai.android.files.databinding.FileListFragmentSpeedDialIncludeBinding
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
import me.zhanghai.android.files.navigation.NavigationFragment
import me.zhanghai.android.files.navigation.NavigationRootMapLiveData
import me.zhanghai.android.files.provider.archive.createArchiveRootPath
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.terminal.Terminal
import me.zhanghai.android.files.theme.MftlrsTheme
import me.zhanghai.android.files.ui.CoordinatorAppBarLayout
import me.zhanghai.android.files.ui.DrawerLayoutOnBackPressedCallback
import me.zhanghai.android.files.ui.PersistentBarLayout
import me.zhanghai.android.files.ui.PersistentDrawerLayout
import me.zhanghai.android.files.util.DebouncedRunnable
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.Stateful
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
import me.zhanghai.android.files.util.getQuantityString
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
    NavigationFragment.Listener, ShowRequestAllFilesAccessRationaleDialogFragment.Listener,
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

    private lateinit var binding: Binding

    private lateinit var navigationFragment: NavigationFragment

    // mftlrs: the file list is FileListContent in Compose.
    private val contentState = FileListContentState()

    // mftlrs: the toolbar with its menu and search is FileListTopBar in Compose.
    private val topBarState = FileListTopBarState()

    // mftlrs: the bottom bar was a toolbar action mode; it is FileListBottomBar in Compose.
    private val bottomBarState = FileListBottomBarState()
    private var isBottomBarShown = false

    // mftlrs: the speed dial is FileListFab in Compose.
    private val fabState = FileListFabState()
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
        Binding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState == null) {
            navigationFragment = NavigationFragment()
            childFragmentManager.commit { add(R.id.navigationFragment, navigationFragment) }
        } else {
            navigationFragment = childFragmentManager.findFragmentById(R.id.navigationFragment)
                as NavigationFragment
        }
        navigationFragment.listener = this
        requireActivity().setTitle(R.string.file_list_title)
        topBarState.title = getString(R.string.file_list_title) // pickOptionsLiveData без значения, пока не выбор
        // mftlrs: no support action bar, the toolbar is in Compose.
        binding.toolbarComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MftlrsTheme {
                    FileListTopBar(
                        state = topBarState,
                        onNavigationClick = { onNavigationClick() },
                        onSearchExpand = { expandSearchView() },
                        onSearchCollapse = { collapseSearchView() },
                        onSearchQueryChange = { onSearchQueryChange(it) },
                        onSearchSubmit = { onSearchSubmit(it) },
                        onAction = { onTopBarAction(it) },
                        onSelectionClose = { clearSelectedFiles() },
                        onSelectionAction = { onSelectionAction(it) },
                    )
                }
            }
        }
        binding.bottomBarComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MftlrsTheme {
                    FileListBottomBar(
                        state = bottomBarState,
                        onClose = { onBottomBarClose() },
                        onAction = { onBottomBarAction(it) },
                    )
                }
            }
        }
        savedInstanceState?.getString(STATE_CREATE_FILE_NAME)
            ?.let { bottomBarState.fileName.setTextAndPlaceCursorAtEnd(it) }
        val contentLayoutInitialPaddingBottom = binding.contentLayout.paddingBottom
        binding.appBarLayout.addOnOffsetChangedListener { _, verticalOffset ->
            binding.contentLayout.updatePaddingRelative(
                bottom = contentLayoutInitialPaddingBottom +
                    binding.appBarLayout.totalScrollRange + verticalOffset
            )
        }
        // mftlrs: the breadcrumbs are FileListBreadcrumbs in Compose.
        binding.breadcrumbComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MftlrsTheme {
                    FileListBreadcrumbs(
                        data = viewModel.breadcrumbLiveData.observeAsState().value,
                        onNavigate = { navigateTo(it) },
                        onCopyPath = { copyPath(it) },
                        onOpenInNewTask = { openInNewTask(it) },
                    )
                }
            }
        }
        binding.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setOnApplyWindowInsetsListener { _, insets ->
                contentState.bottomInset = insets.systemWindowInsetBottom
                insets
            }
            setContent {
                MftlrsTheme {
                    FileListContent(contentState, this@FileListFragment, onRefresh = { refresh() })
                }
            }
        }
        savedInstanceState?.getParcelableSafe<ScrollPosition>(STATE_SCROLL_POSITION)
            ?.let { contentState.scrollTo(it) }
        binding.fabComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setOnApplyWindowInsetsListener { _, insets ->
                fabState.bottomInset = insets.systemWindowInsetBottom
                fabState.endInset = insets.systemWindowInsetRight
                insets
            }
            setContent {
                MftlrsTheme {
                    FileListFab(
                        state = fabState,
                        onExpandedChange = { setFabExpanded(it) },
                        onAction = { onFabAction(it) },
                    )
                }
            }
        }

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
        binding.drawerLayout?.let {
            addOnBackPressedCallback(DrawerLayoutOnBackPressedCallback(it))
        }

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
        viewModel.currentPathLiveData.observe(viewLifecycleOwner) { onCurrentPathChanged(it) }
        viewModel.searchViewExpandedLiveData.observe(viewLifecycleOwner) {
            onSearchViewExpandedChanged(it)
        }
        viewModel.viewTypeLiveData.observe(viewLifecycleOwner) { onViewTypeChanged(it) }
        // Live data only calls observeForever() on its sources when it is active, so we have to
        // make view type live data active first (so that it can load its initial value) before we
        // register another observer that needs to get the view type.
        if (binding.persistentDrawerLayout != null) {
            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.observe(viewLifecycleOwner) {
                onPersistentDrawerOpenChanged(it)
            }
        }
        viewModel.sortOptionsLiveData.observe(viewLifecycleOwner) { onSortOptionsChanged(it) }
        viewModel.viewSortPathSpecificLiveData.observe(viewLifecycleOwner) {
            onViewSortPathSpecificChanged(it)
        }
        viewModel.pickOptionsLiveData.observe(viewLifecycleOwner) { onPickOptionsChanged(it) }
        viewModel.selectedFilesLiveData.observe(viewLifecycleOwner) { onSelectedFilesChanged(it) }
        viewModel.pasteStateLiveData.observe(viewLifecycleOwner) { onPasteStateChanged(it) }
        Settings.FILE_NAME_ELLIPSIZE.observe(viewLifecycleOwner) { onFileNameEllipsizeChanged(it) }
        viewModel.fileListLiveData.observe(viewLifecycleOwner) { onFileListChanged(it) }
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.observe(viewLifecycleOwner) {
            onShowHiddenFilesChanged(it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (view != null) {
            outState.putParcelable(STATE_SCROLL_POSITION, contentState.scrollPosition)
            // mftlrs: the EditText and the speed dial saved these themselves.
            outState.putString(STATE_CREATE_FILE_NAME, bottomBarState.fileName.text.toString())
            outState.putBoolean(STATE_FAB_EXPANDED, fabState.isExpanded)
        }
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
        topBarState.searchQuery = viewModel.searchViewQuery
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
        binding.drawerLayout?.openDrawer(GravityCompat.START)
        if (binding.persistentDrawerLayout != null) {
            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.putValue(
                !Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat
            )
        }
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
        // mftlrs: alphabetic shortcut of menu/file_list_paste.xml; a disabled item did not react.
        if (isBottomBarShown && keyCode == KeyEvent.KEYCODE_V && bottomBarState.isActionEnabled) {
            val action = bottomBarState.action
            if (action == FileListBottomBarAction.PASTE || action == FileListBottomBarAction.EXTRACT_HERE) {
                onBottomBarAction(action)
                return true
            }
        }
        // mftlrs: alphabetic shortcuts of menu/file_list_select.xml and file_list_pick.xml.
        if (topBarState.isSelectionMode) {
            val selectionAction = topBarState.selectionActions.firstOrNull {
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
                if (topBarState.isSelectAllVisible) FileListTopBarAction.SELECT_ALL else null
            KeyEvent.KEYCODE_H -> FileListTopBarAction.SHOW_HIDDEN_FILES
            else -> null
        } ?: return false
        onTopBarAction(action)
        return true
    }

    private fun onPersistentDrawerOpenChanged(open: Boolean) {
        binding.persistentDrawerLayout?.let {
            if (open) {
                it.openDrawer(GravityCompat.START)
            } else {
                it.closeDrawer(GravityCompat.START)
            }
        }
    }

    private fun onCurrentPathChanged(path: Path) {
        updateSelectionBar()
        updateBottomBar()
    }

    private fun onSearchViewExpandedChanged(expanded: Boolean) {
        if (expanded) {
            topBarState.searchQuery = viewModel.searchViewQuery
        }
        topBarState.isSearchExpanded = expanded
    }

    private fun onFileListChanged(stateful: Stateful<List<FileItem>>) {
        val files = stateful.value
        val isSearching = viewModel.searchState.isSearching
        topBarState.subtitle = when {
            stateful is Failure -> getString(R.string.error)
            stateful is Loading && !isSearching -> getString(R.string.loading)
            else -> getSubtitle(files!!)
        }
        val hasFiles = !files.isNullOrEmpty()
        contentState.isRefreshing = stateful is Loading && (hasFiles || isSearching)
        contentState.isLoading = stateful is Loading && !(hasFiles || isSearching)
        var errorText: String? = null
        val throwable = (stateful as? Failure)?.throwable
        if (throwable != null) {
            throwable.printStackTrace()
            val error = throwable.toString()
            if (hasFiles) {
                showToast(error)
            } else {
                errorText = error
            }
        }
        contentState.errorText = errorText
        contentState.isEmpty = stateful is Success && !hasFiles
        if (files != null) {
            updateAdapterFileList()
        } else {
            // mftlrs: a new directory starts from the top, as the cleared RecyclerView did.
            contentState.files = emptyList()
            contentState.scrollTo(ScrollPosition(0, 0))
        }
        if (stateful is Success) {
            (viewModel.pendingState as? ScrollPosition)?.let { contentState.scrollTo(it) }
        }
    }

    private fun getSubtitle(files: List<FileItem>): String {
        val directoryCount = files.count { it.attributes.isDirectory }
        val fileCount = files.size - directoryCount
        val directoryCountText = if (directoryCount > 0) {
            getQuantityString(
                R.plurals.file_list_subtitle_directory_count_format, directoryCount, directoryCount
            )
        } else {
            null
        }
        val fileCountText = if (fileCount > 0) {
            getQuantityString(
                R.plurals.file_list_subtitle_file_count_format, fileCount, fileCount
            )
        } else {
            null
        }
        return when {
            !directoryCountText.isNullOrEmpty() && !fileCountText.isNullOrEmpty() ->
                (directoryCountText + getString(R.string.file_list_subtitle_separator)
                    + fileCountText)
            !directoryCountText.isNullOrEmpty() -> directoryCountText
            !fileCountText.isNullOrEmpty() -> fileCountText
            else -> getString(R.string.empty)
        }
    }

    private fun onViewTypeChanged(viewType: FileViewType) {
        // mftlrs: the column count is computed by FileListContent from its actual width.
        contentState.viewType = viewType
        topBarState.viewType = viewType
    }

    private fun onSortOptionsChanged(sortOptions: FileSortOptions) {
        updateAdapterFileList()
        topBarState.sortOptions = sortOptions
    }

    private fun onViewSortPathSpecificChanged(pathSpecific: Boolean) {
        topBarState.isViewSortPathSpecific = pathSpecific
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

    private fun onShowHiddenFilesChanged(showHiddenFiles: Boolean) {
        updateAdapterFileList()
        topBarState.showHiddenFiles = showHiddenFiles
    }

    // mftlrs: sorting moved here from the removed FileListAdapter.
    private fun updateAdapterFileList() {
        // mftlrs: may be called by sort options before the file list has a value.
        var files = viewModel.fileListLiveData.value?.value ?: return
        if (!Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat) {
            files = files.filterNot { it.isHidden }
        }
        val isSearching = viewModel.searchState.isSearching
        if (!isSearching) {
            files = files.sortedWith(viewModel.sortOptions.createComparator())
        }
        if (isSearching != lastIsSearching) {
            lastIsSearching = isSearching
            contentState.scrollTo(ScrollPosition(0, 0))
        }
        contentState.files = files
    }

    private var lastIsSearching = false

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
        viewModel.navigateTo(contentState.scrollPosition, path)
    }

    private fun copyPath(path: Path) {
        clipboardManager.copyText(path.toUserFriendlyString(), requireContext())
    }

    private fun openInNewTask(path: Path) {
        val intent = FileListActivity.createViewIntent(path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        startActivitySafe(intent)
    }

    private fun onPickOptionsChanged(pickOptions: PickOptions?) {
        val title = if (pickOptions == null) {
            getString(R.string.file_list_title)
        } else {
            val count = if (pickOptions.allowMultiple) Int.MAX_VALUE else 1
            when (pickOptions.mode) {
                PickOptions.Mode.OPEN_FILE ->
                    getQuantityString(R.plurals.file_list_title_open_file, count)
                PickOptions.Mode.CREATE_FILE -> getString(R.string.file_list_title_create_file)
                PickOptions.Mode.OPEN_DIRECTORY ->
                    getQuantityString(R.plurals.file_list_title_open_directory, count)
            }
        }
        requireActivity().title = title
        topBarState.title = title
        topBarState.isSelectAllVisible = pickOptions == null || pickOptions.allowMultiple
        updateSelectionBar()
        updateBottomBar()
        contentState.pickOptions = pickOptions
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

    private fun onSelectedFilesChanged(files: FileItemSet) {
        updateSelectionBar()
        contentState.selectedPaths = files.mapTo(mutableSetOf()) { it.path }
    }

    // mftlrs: the selection bar is in FileListTopBar; was updateOverlayToolbar() with
    // menu/file_list_select.xml and file_list_pick.xml.
    private fun updateSelectionBar() {
        val files = viewModel.selectedFiles
        if (files.isEmpty()) {
            topBarState.isSelectionMode = false
            return
        }
        topBarState.selectionTitle = getString(R.string.file_list_select_title_format, files.size)
        val pickOptions = viewModel.pickOptions
        topBarState.selectionActions = if (pickOptions != null) {
            val isOpen = when (pickOptions.mode) {
                PickOptions.Mode.OPEN_FILE, PickOptions.Mode.OPEN_DIRECTORY -> true
                PickOptions.Mode.CREATE_FILE -> false
            }
            buildList {
                add(if (isOpen) FileListSelectionAction.OPEN else FileListSelectionAction.CREATE)
                if (pickOptions.allowMultiple) {
                    add(FileListSelectionAction.SELECT_ALL)
                }
            }
        } else {
            val isAnyFileReadOnly = files.any { it.path.fileSystem.isReadOnly }
            val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
            val areAllFilesArchiveFiles = files.all { it.isArchiveFile }
            val isCurrentPathReadOnly = viewModel.currentPath.fileSystem.isReadOnly
            buildList {
                if (!isAnyFileReadOnly) {
                    add(FileListSelectionAction.CUT)
                }
                add(
                    if (areAllFilesArchivePaths) {
                        FileListSelectionAction.COPY_EXTRACT
                    } else {
                        FileListSelectionAction.COPY
                    }
                )
                if (!isAnyFileReadOnly) {
                    add(FileListSelectionAction.DELETE)
                }
                if (areAllFilesArchiveFiles) {
                    add(FileListSelectionAction.EXTRACT)
                }
                if (!isCurrentPathReadOnly) {
                    add(FileListSelectionAction.ARCHIVE)
                }
                add(FileListSelectionAction.SHARE)
                add(FileListSelectionAction.SELECT_ALL)
            }
        }
        if (!topBarState.isSelectionMode) {
            binding.appBarLayout.setExpanded(true)
            topBarState.isSelectionMode = true
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
        bottomBarState.fileName.edit {
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
        val files = contentState.files.filterTo(fileItemSetOf()) { isFileSelectable(it, pickOptions) }
        selectFiles(files, true)
    }

    private fun onPasteStateChanged(pasteState: PasteState) {
        updateBottomBar()
    }

    // mftlrs: was updateBottomToolbar() with menu/file_list_paste.xml and file_list_pick_bottom.xml.
    private fun updateBottomBar() {
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            when (pickOptions.mode) {
                PickOptions.Mode.CREATE_FILE -> {
                    bottomBarState.isCreateFileMode = true
                    bottomBarState.action = FileListBottomBarAction.CREATE
                    if (!viewModel.isCreateFileNameEditInitialized) {
                        setCreateFileName(pickOptions.fileName!!)
                        bottomBarState.isFileNameFocusRequested = true
                        viewModel.isCreateFileNameEditInitialized = true
                    }
                }
                PickOptions.Mode.OPEN_DIRECTORY -> {
                    val path = viewModel.currentPath
                    val navigationRoot = NavigationRootMapLiveData.valueCompat[path]
                    val name = navigationRoot?.getName(requireContext()) ?: path.name
                    bottomBarState.title =
                        getString(R.string.file_list_open_current_directory_format, name)
                    bottomBarState.isCreateFileMode = false
                    bottomBarState.action = FileListBottomBarAction.OPEN
                }
                else -> {
                    hideBottomBar()
                    return
                }
            }
            bottomBarState.isActionEnabled = true
        } else {
            val pasteState = viewModel.pasteState
            val files = pasteState.files
            if (files.isEmpty()) {
                hideBottomBar()
                return
            }
            val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
            bottomBarState.title = getString(
                if (pasteState.copy) {
                    if (areAllFilesArchivePaths) {
                        R.string.file_list_paste_extract_title_format
                    } else {
                        R.string.file_list_paste_copy_title_format
                    }
                } else {
                    R.string.file_list_paste_move_title_format
                }, files.size
            )
            bottomBarState.isCreateFileMode = false
            bottomBarState.action = if (areAllFilesArchivePaths) {
                FileListBottomBarAction.EXTRACT_HERE
            } else {
                FileListBottomBarAction.PASTE
            }
            bottomBarState.isActionEnabled = !viewModel.currentPath.fileSystem.isReadOnly
        }
        if (!isBottomBarShown) {
            isBottomBarShown = true
            binding.persistentBarLayout.showBar(binding.bottomBarLayout)
        }
    }

    // mftlrs: was finishing the bottom action mode.
    private fun hideBottomBar() {
        if (!isBottomBarShown) {
            return
        }
        isBottomBarShown = false
        binding.persistentBarLayout.hideBar(binding.bottomBarLayout)
        if (viewModel.pickOptions == null) {
            viewModel.clearPasteState()
        }
    }

    private fun onBottomBarClose() {
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            requireActivity().finish()
        } else {
            hideBottomBar()
        }
    }

    private fun onBottomBarAction(action: FileListBottomBarAction) {
        when (action) {
            FileListBottomBarAction.OPEN -> pickPaths(linkedSetOf(viewModel.currentPath))
            FileListBottomBarAction.CREATE -> {
                val fileName = bottomBarState.fileName.text.toString()
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
        fabState.isExpanded = expanded
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

    private fun onFileNameEllipsizeChanged(fileNameEllipsize: TextUtils.TruncateAt) {
        contentState.nameEllipsize = fileNameEllipsize
    }

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
        for (file in contentState.files) {
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

    override fun navigateToDefaultRoot() {
        navigateToRoot(Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat)
    }

    override fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit) {
        viewModel.currentPathLiveData.observe(owner, observer)
    }

    override fun closeNavigationDrawer() {
        binding.drawerLayout?.closeDrawer(GravityCompat.START)
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

    private class Binding private constructor(
        val root: View,
        val drawerLayout: DrawerLayout? = null,
        val persistentDrawerLayout: PersistentDrawerLayout? = null,
        val persistentBarLayout: PersistentBarLayout,
        val appBarLayout: CoordinatorAppBarLayout,
        val toolbarComposeView: ComposeView,
        val breadcrumbComposeView: ComposeView,
        val contentLayout: ViewGroup,
        val composeView: ComposeView,
        val bottomBarLayout: ViewGroup,
        val bottomBarComposeView: ComposeView,
        val fabComposeView: ComposeView
    ) {
        companion object {
            fun inflate(
                inflater: LayoutInflater,
                root: ViewGroup?,
                attachToRoot: Boolean
            ): Binding {
                val binding = FileListFragmentBinding.inflate(inflater, root, attachToRoot)
                val bindingRoot = binding.root
                val includeBinding = FileListFragmentIncludeBinding.bind(bindingRoot)
                val appBarBinding = FileListFragmentAppBarIncludeBinding.bind(bindingRoot)
                val contentBinding = FileListFragmentContentIncludeBinding.bind(bindingRoot)
                val bottomBarBinding = FileListFragmentBottomBarIncludeBinding.bind(bindingRoot)
                val speedDialBinding = FileListFragmentSpeedDialIncludeBinding.bind(bindingRoot)
                return Binding(
                    bindingRoot, includeBinding.drawerLayout, includeBinding.persistentDrawerLayout,
                    includeBinding.persistentBarLayout, appBarBinding.appBarLayout,
                    appBarBinding.toolbarComposeView, appBarBinding.breadcrumbComposeView, contentBinding.contentLayout,
                    contentBinding.composeView, bottomBarBinding.bottomBarLayout,
                    bottomBarBinding.bottomBarComposeView, speedDialBinding.fabComposeView
                )
            }
        }
    }
}
