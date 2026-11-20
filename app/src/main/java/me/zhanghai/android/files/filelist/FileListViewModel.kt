/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 * Modified for mftlrs, 2026.
 */

package me.zhanghai.android.files.filelist

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.text.TextUtils
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import java8.nio.file.Path
import java8.nio.file.Paths
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.file.extension
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.FileSortOptions.By
import me.zhanghai.android.files.filelist.FileSortOptions.Order
import me.zhanghai.android.files.navigation.BookmarkDirectories
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.provider.archive.archiveRefresh
import me.zhanghai.android.files.provider.archive.createArchiveRootPath
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.CloseableLiveData
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.takeIfNotEmpty
import me.zhanghai.android.files.util.valueCompat
import java.io.Closeable

// TODO: Use SavedStateHandle to save state.
// mftlrs: also the actions of the main screen, which were in FileListFragment; it executes the
// one-time events and is still the listener of the dialogs.
class FileListViewModel : ViewModel(), FileItemListener {
    private val trailLiveData = TrailLiveData()
    val hasTrail: Boolean
        get() = trailLiveData.value != null
    val pendingState: Parcelable?
        get() = trailLiveData.valueCompat.pendingState

    // mftlrs: the scroll position of the list, updated by FileListFragment; saved in the trail
    // when navigating away, as the state of GridLayoutManager was.
    var scrollPosition = ScrollPosition(0, 0)

    fun navigateTo(path: Path) {
        collapseSearch()
        trailLiveData.navigateTo(scrollPosition, path)
    }

    fun navigateToRoot(path: Path) {
        collapseSearch()
        resetTo(path)
    }

    fun resetTo(path: Path) = trailLiveData.resetTo(path)

    fun navigateUp(): Boolean = trailLiveData.navigateUp()

    val currentPathLiveData = trailLiveData.map { it.currentPath }
    val currentPath: Path
        get() = currentPathLiveData.valueCompat

    private val _searchStateLiveData = MutableLiveData(SearchState(false, ""))
    val searchStateLiveData: LiveData<SearchState> = _searchStateLiveData
    val searchState: SearchState
        get() = _searchStateLiveData.valueCompat

    fun search(query: String) {
        val searchState = _searchStateLiveData.valueCompat
        if (searchState.isSearching && searchState.query == query) {
            return
        }
        _searchStateLiveData.value = SearchState(true, query)
    }

    fun stopSearching() {
        val searchState = _searchStateLiveData.valueCompat
        if (!searchState.isSearching) {
            return
        }
        _searchStateLiveData.value = SearchState(false, "")
    }

    private val _fileListLiveData =
        FileListSwitchMapLiveData(currentPathLiveData, _searchStateLiveData)
    val fileListLiveData: LiveData<Stateful<List<FileItem>>>
        get() = _fileListLiveData
    val fileListStateful: Stateful<List<FileItem>>
        get() = _fileListLiveData.valueCompat

    fun reload() {
        val path = currentPath
        if (path.isArchivePath) {
            path.archiveRefresh()
        }
        _fileListLiveData.reload()
    }

    val searchViewExpandedLiveData = MutableLiveData(false)
    var isSearchViewExpanded: Boolean
        get() = searchViewExpandedLiveData.valueCompat
        set(value) {
            if (searchViewExpandedLiveData.valueCompat == value) {
                return
            }
            searchViewExpandedLiveData.value = value
        }

    private val _searchViewQueryLiveData = MutableLiveData("")
    var searchViewQuery: String
        get() = _searchViewQueryLiveData.valueCompat
        set(value) {
            if (_searchViewQueryLiveData.valueCompat == value) {
                return
            }
            _searchViewQueryLiveData.value = value
        }

    val breadcrumbLiveData: LiveData<BreadcrumbData> = BreadcrumbLiveData(trailLiveData)
    val canNavigateUpBreadcrumb: Boolean
        get() = breadcrumbLiveData.valueCompat.selectedIndex > 0

    private val _viewTypeLiveData = FileViewTypeLiveData(currentPathLiveData)
    val viewTypeLiveData: LiveData<FileViewType> = _viewTypeLiveData
    var viewType: FileViewType
        get() = _viewTypeLiveData.valueCompat
        set(value) {
            _viewTypeLiveData.putValue(value)
        }

    private val _sortOptionsLiveData = FileSortOptionsLiveData(currentPathLiveData)
    val sortOptionsLiveData: LiveData<FileSortOptions> = _sortOptionsLiveData
    val sortOptions: FileSortOptions
        get() = _sortOptionsLiveData.valueCompat

    fun setSortBy(by: By) = _sortOptionsLiveData.putBy(by)

    fun setSortOrder(order: Order) = _sortOptionsLiveData.putOrder(order)

    fun setSortDirectoriesFirst(isDirectoriesFirst: Boolean) =
        _sortOptionsLiveData.putIsDirectoriesFirst(isDirectoriesFirst)

    private val _viewSortPathSpecificLiveData =
        FileViewSortPathSpecificLiveData(currentPathLiveData)
    val viewSortPathSpecificLiveData: LiveData<Boolean>
        get() = _viewSortPathSpecificLiveData
    var isViewSortPathSpecific: Boolean
        get() = _viewSortPathSpecificLiveData.valueCompat
        set(value) {
            _viewSortPathSpecificLiveData.putValue(value)
        }

    private val _pickOptionsLiveData = MutableLiveData<PickOptions?>()
    val pickOptionsLiveData: LiveData<PickOptions?>
        get() = _pickOptionsLiveData
    var pickOptions: PickOptions?
        get() = _pickOptionsLiveData.value
        set(value) {
            _pickOptionsLiveData.value = value
        }

    var isCreateFileNameEditInitialized: Boolean = false

    private val _selectedFilesLiveData = MutableLiveData(fileItemSetOf())
    val selectedFilesLiveData: LiveData<FileItemSet>
        get() = _selectedFilesLiveData
    val selectedFiles: FileItemSet
        get() = _selectedFilesLiveData.valueCompat

    override fun selectFile(file: FileItem, selected: Boolean) {
        selectFiles(fileItemSetOf(file), selected)
    }

    override fun selectFiles(files: FileItemSet, selected: Boolean) {
        val selectedFiles = _selectedFilesLiveData.valueCompat
        // mftlrs: the logic is in FileListUiLogic.kt, for unit tests.
        if (selectedFiles.select(files, selected)) {
            _selectedFilesLiveData.value = selectedFiles
        }
    }

    fun replaceSelectedFiles(files: FileItemSet) {
        val selectedFiles = _selectedFilesLiveData.valueCompat
        if (selectedFiles == files) {
            return
        }
        selectedFiles.clear()
        selectedFiles.addAll(files)
        _selectedFilesLiveData.value = selectedFiles
    }

    override fun clearSelectedFiles() {
        val selectedFiles = _selectedFilesLiveData.valueCompat
        if (selectedFiles.isEmpty()) {
            return
        }
        selectedFiles.clear()
        _selectedFilesLiveData.value = selectedFiles
    }

    val pasteStateLiveData: LiveData<PasteState> = _pasteStateLiveData
    val pasteState: PasteState
        get() = _pasteStateLiveData.valueCompat

    fun addToPasteState(copy: Boolean, files: FileItemSet) {
        val pasteState = _pasteStateLiveData.valueCompat
        // mftlrs: the logic is in FileListUiLogic.kt, for unit tests.
        if (pasteState.add(copy, files)) {
            _pasteStateLiveData.value = pasteState
        }
    }

    fun clearPasteState() {
        val pasteState = _pasteStateLiveData.valueCompat
        if (pasteState.files.isEmpty()) {
            return
        }
        pasteState.files.clear()
        _pasteStateLiveData.value = pasteState
    }

    private val _isRequestingStorageAccessLiveData = MutableLiveData(false)
    var isStorageAccessRequested: Boolean
        get() = _isRequestingStorageAccessLiveData.valueCompat
        set(value) {
            _isRequestingStorageAccessLiveData.value = value
        }

    private val _isRequestingNotificationPermissionLiveData = MutableLiveData(false)
    var isNotificationPermissionRequested: Boolean
        get() = _isRequestingNotificationPermissionLiveData.valueCompat
        set(value) {
            _isRequestingNotificationPermissionLiveData.value = value
        }

    // mftlrs: the screen state for Compose, computed here instead of in the observers of
    // FileListFragment. No strings: Compose builds the text. The live data above are observed only
    // while the screen collects the state, otherwise FileListLiveData would reload in background.
    private val _uiState = MutableStateFlow(FileListUiState())
    internal val uiState: StateFlow<FileListUiState> = flow {
        coroutineScope {
            launch { currentPathLiveData.asFlow().collect { onCurrentPathChanged() } }
            launch { searchViewExpandedLiveData.asFlow().collect { onSearchViewExpandedChanged(it) } }
            launch { _searchViewQueryLiveData.asFlow().collect { onSearchViewQueryChanged(it) } }
            launch { viewTypeLiveData.asFlow().collect { onViewTypeChanged(it) } }
            launch { sortOptionsLiveData.asFlow().collect { onSortOptionsChanged(it) } }
            launch {
                viewSortPathSpecificLiveData.asFlow().collect { onViewSortPathSpecificChanged(it) }
            }
            launch { pickOptionsLiveData.asFlow().collect { onPickOptionsChanged(it) } }
            launch { selectedFilesLiveData.asFlow().collect { onSelectedFilesChanged(it) } }
            launch { pasteStateLiveData.asFlow().collect { updateBottomBar() } }
            launch { Settings.FILE_NAME_ELLIPSIZE.asFlow().collect { onFileNameEllipsizeChanged(it) } }
            launch { fileListLiveData.asFlow().collect { onFileListChanged(it) } }
            launch {
                Settings.FILE_LIST_SHOW_HIDDEN_FILES.asFlow().collect { onShowHiddenFilesChanged(it) }
            }
            emitAll(_uiState)
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    // mftlrs: one-time events for FileListFragment.
    private val _events = Channel<FileListEvent>(Channel.BUFFERED)
    internal val events: Flow<FileListEvent> = _events.receiveAsFlow()

    private var lastIsSearching = false

    fun setFabExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isFabExpanded = expanded) }
    }

    fun setNavigationOpen(open: Boolean) {
        _uiState.update { it.copy(isNavigationOpen = open) }
    }

    private fun onCurrentPathChanged() {
        updateSelectionBar()
        updateBottomBar()
    }

    private fun onSearchViewExpandedChanged(expanded: Boolean) {
        _uiState.update { it.copy(topBar = it.topBar.copy(isSearchExpanded = expanded)) }
    }

    private fun onSearchViewQueryChanged(query: String) {
        _uiState.update { it.copy(topBar = it.topBar.copy(searchQuery = query)) }
    }

    private fun onViewTypeChanged(viewType: FileViewType) {
        _uiState.update {
            it.copy(
                content = it.content.copy(viewType = viewType),
                topBar = it.topBar.copy(viewType = viewType),
            )
        }
    }

    private fun onSortOptionsChanged(sortOptions: FileSortOptions) {
        _uiState.update { it.copy(topBar = it.topBar.copy(sortOptions = sortOptions)) }
        updateFiles()
    }

    private fun onViewSortPathSpecificChanged(pathSpecific: Boolean) {
        _uiState.update { it.copy(topBar = it.topBar.copy(isViewSortPathSpecific = pathSpecific)) }
    }

    private fun onShowHiddenFilesChanged(showHiddenFiles: Boolean) {
        _uiState.update { it.copy(topBar = it.topBar.copy(showHiddenFiles = showHiddenFiles)) }
        updateFiles()
    }

    private fun onFileNameEllipsizeChanged(fileNameEllipsize: TextUtils.TruncateAt) {
        _uiState.update { it.copy(content = it.content.copy(nameEllipsize = fileNameEllipsize)) }
    }

    private fun onFileListChanged(stateful: Stateful<List<FileItem>>) {
        val files = stateful.value
        val isSearching = searchState.isSearching
        val throwable = (stateful as? Failure)?.throwable
        if (throwable != null) {
            throwable.printStackTrace()
            if (!files.isNullOrEmpty()) {
                _events.trySend(FileListEvent.ShowError(throwable.toString()))
            }
        }
        _uiState.update {
            it.copy(
                content = it.content.withStatus(stateful, isSearching),
                topBar = it.topBar.copy(subtitle = fileListSubtitle(stateful, isSearching)),
            )
        }
        if (files != null) {
            updateFiles()
        } else {
            // A new directory starts from the top, as the cleared RecyclerView did.
            _uiState.update { it.copy(content = it.content.copy(files = emptyList())) }
            _events.trySend(FileListEvent.ScrollTo(ScrollPosition(0, 0)))
        }
        if (stateful is Success) {
            (pendingState as? ScrollPosition)?.let { _events.trySend(FileListEvent.ScrollTo(it)) }
        }
    }

    // Was FileListFragment.updateAdapterFileList(), and sorting in the removed FileListAdapter.
    private fun updateFiles() {
        // May be called by sort options before the file list has a value.
        val allFiles = _fileListLiveData.value?.value ?: return
        val topBar = _uiState.value.topBar
        val isSearching = searchState.isSearching
        val files =
            filterAndSortFiles(allFiles, topBar.showHiddenFiles, topBar.sortOptions, isSearching)
        if (isSearching != lastIsSearching) {
            lastIsSearching = isSearching
            _events.trySend(FileListEvent.ScrollTo(ScrollPosition(0, 0)))
        }
        _uiState.update { it.copy(content = it.content.copy(files = files)) }
    }

    private fun onPickOptionsChanged(pickOptions: PickOptions?) {
        _uiState.update {
            it.copy(
                content = it.content.copy(pickOptions = pickOptions),
                topBar = it.topBar.copy(
                    pickOptions = pickOptions,
                    isSelectAllVisible = pickOptions == null || pickOptions.allowMultiple,
                ),
            )
        }
        updateSelectionBar()
        updateBottomBar()
    }

    private fun onSelectedFilesChanged(files: FileItemSet) {
        updateSelectionBar()
        // A snapshot: FileItemSet is changed in place.
        val selectedPaths = files.mapTo(mutableSetOf()) { it.path }
        _uiState.update { it.copy(content = it.content.copy(selectedPaths = selectedPaths)) }
    }

    // Was FileListFragment.updateOverlayToolbar(). When hidden, the bar fades out with its old
    // title and actions.
    private fun updateSelectionBar() {
        val files = selectedFiles
        if (files.isEmpty()) {
            _uiState.update { it.copy(selectionBar = it.selectionBar.copy(isVisible = false)) }
            return
        }
        val actions = selectionBarActions(files, pickOptions, currentPath)
        val selectionBar = FileListUiState.SelectionBar(true, files.size, actions)
        _uiState.update { it.copy(selectionBar = selectionBar) }
    }

    // Was FileListFragment.updateBottomToolbar(). When hidden, the bar slides out with its old
    // content.
    private fun updateBottomBar() {
        val bottomBar = fileListBottomBar(pickOptions, pasteState, currentPath)
        _uiState.update { it.copy(bottomBar = bottomBar ?: it.bottomBar.copy(isVisible = false)) }
    }

    // mftlrs: was in FileListFragment.onActivityCreated(): the start path and the pick mode from
    // the intent of FileListActivity.
    fun start(intent: Intent) {
        if (hasTrail) {
            return
        }
        var path = intent.extraPath
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
        resetTo(path)
        if (pickOptions != null) {
            this.pickOptions = pickOptions
        }
    }

    // mftlrs: the search was a SearchView in the options menu of FileListFragment.
    private var searchJob: Job? = null

    fun expandSearch() {
        isSearchViewExpanded = true
        debounceSearch()
    }

    fun collapseSearch() {
        if (!isSearchViewExpanded) {
            return
        }
        isSearchViewExpanded = false
        stopSearching()
    }

    fun onSearchQueryChange(query: String) {
        searchViewQuery = query
        debounceSearch()
    }

    fun onSearchSubmit(query: String) {
        searchJob?.cancel()
        search(query)
    }

    // Was DebouncedRunnable: searches a second after the last change of the query.
    private fun debounceSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DELAY_MILLIS)
            if (!isSearchViewExpanded) {
                return@launch
            }
            val query = searchViewQuery
            if (query.isEmpty()) {
                return@launch
            }
            search(query)
        }
    }

    // mftlrs: was FileListFragment.onOptionsItemSelected().
    internal fun onTopBarAction(action: FileListTopBarAction) {
        when (action) {
            FileListTopBarAction.VIEW_LIST -> viewType = FileViewType.LIST
            FileListTopBarAction.VIEW_GRID -> viewType = FileViewType.GRID
            FileListTopBarAction.SORT_BY_NAME -> setSortBy(By.NAME)
            FileListTopBarAction.SORT_BY_TYPE -> setSortBy(By.TYPE)
            FileListTopBarAction.SORT_BY_SIZE -> setSortBy(By.SIZE)
            FileListTopBarAction.SORT_BY_LAST_MODIFIED -> setSortBy(By.LAST_MODIFIED)
            FileListTopBarAction.SORT_ORDER_ASCENDING ->
                setSortOrder(
                    if (sortOptions.order != Order.ASCENDING) Order.ASCENDING else Order.DESCENDING
                )
            FileListTopBarAction.SORT_DIRECTORIES_FIRST ->
                setSortDirectoriesFirst(!sortOptions.isDirectoriesFirst)
            FileListTopBarAction.VIEW_SORT_PATH_SPECIFIC ->
                isViewSortPathSpecific = !isViewSortPathSpecific
            FileListTopBarAction.NEW_TASK -> sendEvent(FileListEvent.OpenInNewTask(currentPath))
            FileListTopBarAction.NAVIGATE_UP -> {
                collapseSearch()
                navigateUp()
            }
            FileListTopBarAction.NAVIGATE_TO ->
                showDialog(FileListDialog.NavigateToPath(currentPath))
            FileListTopBarAction.REFRESH -> reload()
            FileListTopBarAction.SELECT_ALL -> selectAllFiles()
            FileListTopBarAction.SHOW_HIDDEN_FILES ->
                Settings.FILE_LIST_SHOW_HIDDEN_FILES.putValue(
                    !Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat
                )
            FileListTopBarAction.SHARE -> share(currentPath, MimeType.DIRECTORY)
            FileListTopBarAction.COPY_PATH -> sendEvent(FileListEvent.CopyPath(currentPath))
            FileListTopBarAction.OPEN_IN_TERMINAL -> {
                val path = currentPath
                if (path.isLinuxPath) {
                    sendEvent(FileListEvent.OpenInTerminal(path))
                }
            }
            FileListTopBarAction.ADD_BOOKMARK -> addBookmark(currentPath)
            FileListTopBarAction.CREATE_SHORTCUT ->
                sendEvent(FileListEvent.CreateShortcut(currentPath, MimeType.DIRECTORY))
        }
    }

    // mftlrs: alphabetic shortcuts of the removed menus; a hidden or disabled item doesn't react.
    fun onKeyShortcut(keyCode: Int): Boolean {
        val state = _uiState.value
        val bottomBar = state.bottomBar
        if (bottomBar.isVisible && keyCode == KeyEvent.KEYCODE_V && bottomBar.isActionEnabled) {
            val action = bottomBar.action
            if (action == FileListBottomBarAction.PASTE ||
                action == FileListBottomBarAction.EXTRACT_HERE) {
                pasteFiles(currentPath)
                return true
            }
        }
        if (state.selectionBar.isVisible) {
            val selectionAction = state.selectionBar.actions.firstOrNull {
                when (keyCode) {
                    KeyEvent.KEYCODE_X -> it == FileListSelectionAction.CUT
                    KeyEvent.KEYCODE_C ->
                        it == FileListSelectionAction.COPY ||
                            it == FileListSelectionAction.COPY_EXTRACT
                    KeyEvent.KEYCODE_A -> it == FileListSelectionAction.SELECT_ALL
                    else -> false
                }
            }
            if (selectionAction != null) {
                onSelectionAction(selectionAction)
                return true
            }
        }
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

    // mftlrs: was FileListFragment.onOverlayActionModeMenuItemClicked().
    internal fun onSelectionAction(action: FileListSelectionAction) {
        val files = selectedFiles
        when (action) {
            FileListSelectionAction.OPEN -> pickFiles(files)
            FileListSelectionAction.CREATE -> confirmReplaceFile(files.single())
            FileListSelectionAction.CUT -> cutFiles(files)
            FileListSelectionAction.COPY, FileListSelectionAction.COPY_EXTRACT -> copyFiles(files)
            FileListSelectionAction.DELETE ->
                showDialog(FileListDialog.ConfirmDeleteFiles(files.toFileItemSet()))
            FileListSelectionAction.EXTRACT -> extractFiles(files)
            FileListSelectionAction.ARCHIVE ->
                showDialog(FileListDialog.CreateArchive(files.toFileItemSet()))
            FileListSelectionAction.SHARE -> shareFiles(files)
            FileListSelectionAction.SELECT_ALL -> selectAllFiles()
        }
    }

    fun onBottomBarClose() {
        if (pickOptions != null) {
            sendEvent(FileListEvent.Finish)
        } else {
            clearPasteState()
        }
    }

    // fileName is the text of the file name field, for CREATE.
    internal fun onBottomBarAction(action: FileListBottomBarAction, fileName: String) {
        when (action) {
            FileListBottomBarAction.OPEN -> pick(linkedSetOf(currentPath))
            FileListBottomBarAction.CREATE -> {
                if (fileName.isEmpty()) {
                    sendEvent(FileListEvent.ShowToast(R.string.file_list_create_file_name_error_empty))
                } else if (fileName.asFileNameOrNull() == null) {
                    sendEvent(
                        FileListEvent.ShowToast(R.string.file_list_create_file_name_error_invalid)
                    )
                } else {
                    val file = getFileWithName(fileName)
                    if (file != null) {
                        confirmReplaceFile(file, false)
                    } else {
                        pick(linkedSetOf(currentPath.resolve(fileName)))
                    }
                }
            }
            FileListBottomBarAction.PASTE, FileListBottomBarAction.EXTRACT_HERE ->
                pasteFiles(currentPath)
        }
    }

    internal fun onFabAction(action: FileListFabAction) {
        showDialog(
            when (action) {
                FileListFabAction.CREATE_FILE -> FileListDialog.CreateFile
                FileListFabAction.CREATE_DIRECTORY -> FileListDialog.CreateDirectory
            }
        )
        setFabExpanded(false)
    }

    override fun openFile(file: FileItem) {
        val pickOptions = pickOptions
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
            OpenApkDefaultAction.ASK -> showDialog(FileListDialog.OpenApk(file))
        }
    }

    fun installApk(file: FileItem) {
        val path = file.path
        val uri = path.installPackageUriOrNull()
        if (uri != null) {
            sendEvent(FileListEvent.InstallApk(uri))
        } else {
            FileJobService.installApk(path, application)
        }
    }

    fun viewApk(file: FileItem) {
        navigateTo(file.listablePath)
    }

    override fun openFileWith(file: FileItem) {
        openFileWithIntent(file, true)
    }

    private fun openFileWithIntent(file: FileItem, withChooser: Boolean) {
        val path = file.path
        if (path.isArchivePath) {
            FileJobService.open(path, file.mimeType, withChooser, application)
        } else {
            // The image viewer needs the ordered list on the screen.
            sendEvent(FileListEvent.ViewFile(file, withChooser, _uiState.value.content.files))
        }
    }

    override fun cutFile(file: FileItem) {
        cutFiles(fileItemSetOf(file))
    }

    override fun copyFile(file: FileItem) {
        copyFiles(fileItemSetOf(file))
    }

    override fun confirmDeleteFile(file: FileItem) {
        showDialog(FileListDialog.ConfirmDeleteFiles(fileItemSetOf(file)))
    }

    override fun showRenameFileDialog(file: FileItem) {
        showDialog(FileListDialog.RenameFile(file))
    }

    override fun extractFile(file: FileItem) {
        copyFile(file.createDummyArchiveRoot())
    }

    override fun showCreateArchiveDialog(file: FileItem) {
        showDialog(FileListDialog.CreateArchive(fileItemSetOf(file)))
    }

    override fun shareFile(file: FileItem) {
        share(file.path, file.mimeType)
    }

    override fun copyPath(file: FileItem) {
        sendEvent(FileListEvent.CopyPath(file.path))
    }

    override fun addBookmark(file: FileItem) {
        addBookmark(file.path)
    }

    override fun createShortcut(file: FileItem) {
        sendEvent(FileListEvent.CreateShortcut(file.path, file.mimeType))
    }

    override fun showPropertiesDialog(file: FileItem) {
        showDialog(FileListDialog.Properties(file))
    }

    // mftlrs: results of the dialogs in FileListDialogs.kt. The confirmations close the dialog
    // themselves; the name dialogs return the error for the field, or null when closed.

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    fun replaceFile(file: FileItem) {
        pickFiles(fileItemSetOf(file))
    }

    fun deleteFiles(files: FileItemSet) {
        FileJobService.delete(makePathListForJob(files), application)
        selectFiles(files, false)
    }

    fun onCreateFileOk(text: String): Int? =
        onFileNameOk(text, null) {
            FileJobService.create(currentPath.resolve(it), false, application)
        }

    fun onCreateDirectoryOk(text: String): Int? =
        onFileNameOk(text, null) {
            FileJobService.create(currentPath.resolve(it), true, application)
        }

    fun onRenameFileOk(file: FileItem, text: String): Int? =
        onFileNameOk(text, file.name) {
            FileJobService.rename(file.path, it, application)
            selectFile(file, false)
        }

    // The extension of the type is added to the name; the password only for ZIP.
    internal fun onCreateArchiveOk(
        files: FileItemSet,
        text: String,
        type: CreateArchiveType,
        password: String,
    ): Int? =
        onFileNameOk("${text.trim()}.${type.extension}", null) {
            val archiveFile = currentPath.resolve(it)
            val archivePassword = if (type.isPasswordSupported) password.takeIfNotEmpty() else null
            FileJobService.archive(
                makePathListForJob(files), archiveFile, type.format, type.filter, archivePassword,
                application
            )
            selectFiles(files, false)
        }

    fun onNavigateToPathOk(initialPath: Path, text: String): Int? =
        onNameInput(checkPathName(text.trim(), initialPath.toUserFriendlyString())) {
            navigateTo(it.toPathOrNull()!!)
        }

    internal var createArchiveType: CreateArchiveType
        get() = CreateArchiveType.ofSettingId(Settings.CREATE_ARCHIVE_TYPE.valueCompat)
        set(value) {
            Settings.CREATE_ARCHIVE_TYPE.putValue(value.settingId)
        }

    private fun onFileNameOk(name: String, initialName: String?, onValid: (String) -> Unit): Int? =
        onNameInput(checkFileName(name.trim(), initialName) { getFileWithName(it) != null }, onValid)

    private fun onNameInput(input: NameInput, onValid: (String) -> Unit): Int? {
        when (input) {
            NameInput.Unchanged -> dismissDialog()
            is NameInput.Invalid -> return input.errorRes
            is NameInput.Valid -> {
                dismissDialog()
                onValid(input.name)
            }
        }
        return null
    }

    internal fun showPermissionDialog(dialog: FileListPermissionDialog) {
        _uiState.update { it.copy(permissionDialog = dialog) }
    }

    fun dismissPermissionDialog() {
        _uiState.update { it.copy(permissionDialog = null) }
    }

    fun getFileWithName(name: String): FileItem? {
        val fileListData = fileListStateful
        if (fileListData !is Success) {
            return null
        }
        return fileListData.value.find { it.name == name }
    }

    private fun confirmReplaceFile(file: FileItem, setFileName: Boolean = true) {
        if (setFileName) {
            sendEvent(FileListEvent.SetCreateFileName(file.name))
        }
        showDialog(FileListDialog.ConfirmReplaceFile(file))
    }

    private fun pickFiles(files: FileItemSet) {
        pick(files.mapTo(linkedSetOf()) { it.path })
    }

    private fun pick(paths: LinkedHashSet<Path>) {
        sendEvent(FileListEvent.Pick(paths, pickOptions!!))
    }

    private fun cutFiles(files: FileItemSet) {
        addToPasteState(false, files)
        selectFiles(files, false)
    }

    private fun copyFiles(files: FileItemSet) {
        addToPasteState(true, files)
        selectFiles(files, false)
    }

    private fun extractFiles(files: FileItemSet) {
        copyFiles(files.mapTo(fileItemSetOf()) { it.createDummyArchiveRoot() })
        selectFiles(files, false)
    }

    private fun shareFiles(files: FileItemSet) {
        sendEvent(FileListEvent.Share(files.map { it.path }, files.map { it.mimeType }))
        selectFiles(files, false)
    }

    private fun share(path: Path, mimeType: MimeType) {
        sendEvent(FileListEvent.Share(listOf(path), listOf(mimeType)))
    }

    private fun selectAllFiles() {
        val pickOptions = pickOptions
        val files = _uiState.value.content.files
            .filterTo(fileItemSetOf()) { isFileSelectable(it, pickOptions) }
        selectFiles(files, true)
    }

    private fun pasteFiles(targetDirectory: Path) {
        val pasteState = pasteState
        val sources = makePathListForJob(pasteState.files)
        if (pasteState.copy) {
            FileJobService.copy(sources, targetDirectory, application)
        } else {
            FileJobService.move(sources, targetDirectory, application)
        }
        clearPasteState()
    }

    private fun addBookmark(path: Path) {
        BookmarkDirectories.add(BookmarkDirectory(null, path))
        sendEvent(FileListEvent.ShowToast(R.string.file_add_bookmark_success))
    }

    private fun makePathListForJob(files: FileItemSet): List<Path> =
        files.map { it.path }.sortedBy { it.toUri() }

    // A copy for a dialog: the selection is changed in place.
    private fun FileItemSet.toFileItemSet(): FileItemSet = FileItemSet().apply { addAll(this@toFileItemSet) }

    private fun showDialog(dialog: FileListDialog) {
        _uiState.update { it.copy(dialog = dialog) }
    }

    private fun sendEvent(event: FileListEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        _fileListLiveData.close()
    }

    companion object {
        private val _pasteStateLiveData = MutableLiveData(PasteState())

        // mftlrs: moved from FileListFragment.
        private const val ACTION_VIEW_DOWNLOADS =
            "me.zhanghai.android.files.intent.action.VIEW_DOWNLOADS"

        private const val SEARCH_DELAY_MILLIS = 1000L
    }

    private class FileListSwitchMapLiveData(
        private val pathLiveData: LiveData<Path>,
        private val searchStateLiveData: LiveData<SearchState>
    ) : MediatorLiveData<Stateful<List<FileItem>>>(), Closeable {
        private var liveData: CloseableLiveData<Stateful<List<FileItem>>>? = null

        init {
            addSource(pathLiveData) { updateSource() }
            addSource(searchStateLiveData) { updateSource() }
        }

        private fun updateSource() {
            liveData?.let {
                removeSource(it)
                it.close()
            }
            val path = pathLiveData.valueCompat
            val searchState = searchStateLiveData.valueCompat
            val liveData = if (searchState.isSearching) {
                SearchFileListLiveData(path, searchState.query)
            } else {
                FileListLiveData(path)
            }
            this.liveData = liveData
            addSource(liveData) { value = it }
        }

        fun reload() {
            when (val liveData = liveData) {
                is FileListLiveData -> liveData.loadValue()
                is SearchFileListLiveData -> liveData.loadValue()
            }
        }

        override fun close() {
            liveData?.let {
                removeSource(it)
                it.close()
                this.liveData = null
            }
        }
    }
}

// mftlrs: the main screen state; each part goes to its own composable.
internal data class FileListUiState(
    val content: Content = Content(),
    val topBar: TopBar = TopBar(),
    val selectionBar: SelectionBar = SelectionBar(),
    val bottomBar: BottomBar = BottomBar(),
    val isFabExpanded: Boolean = false,
    val isNavigationOpen: Boolean = false, // the modal navigation drawer
    val dialog: FileListDialog? = null,
    // Separate from dialog: may be shown on resume over another dialog.
    val permissionDialog: FileListPermissionDialog? = null,
) {
    data class Content(
        val files: List<FileItem> = emptyList(), // filtered and sorted
        val isRefreshing: Boolean = false, // loading when there are files already
        val isLoading: Boolean = false, // loading without files
        val errorText: String? = null,
        val isEmpty: Boolean = false,
        val viewType: FileViewType = FileViewType.LIST,
        val selectedPaths: Set<Path> = emptySet(),
        val pickOptions: PickOptions? = null,
        val nameEllipsize: TextUtils.TruncateAt = TextUtils.TruncateAt.END,
    )

    data class TopBar(
        val pickOptions: PickOptions? = null, // the title depends on it
        val subtitle: Subtitle? = null,
        val isSearchExpanded: Boolean = false,
        val searchQuery: String = "", // the initial text of the search field
        val viewType: FileViewType = FileViewType.LIST,
        val sortOptions: FileSortOptions = FileSortOptions(By.NAME, Order.ASCENDING, true),
        val isViewSortPathSpecific: Boolean = false,
        val isSelectAllVisible: Boolean = true,
        val showHiddenFiles: Boolean = false,
    )

    sealed interface Subtitle {
        data object Loading : Subtitle

        data object Error : Subtitle

        data class Counts(val directoryCount: Int, val fileCount: Int) : Subtitle
    }

    data class SelectionBar(
        val isVisible: Boolean = false,
        val count: Int = 0,
        val actions: List<FileListSelectionAction> = emptyList(),
    )

    data class BottomBar(
        val isVisible: Boolean = false,
        val title: BottomBarTitle = BottomBarTitle.FileName,
        val action: FileListBottomBarAction = FileListBottomBarAction.PASTE,
        val isActionEnabled: Boolean = true,
    )

    sealed interface BottomBarTitle {
        data object FileName : BottomBarTitle // the file name field instead of the title

        data class OpenDirectory(val path: Path) : BottomBarTitle

        data class Paste(@StringRes val formatRes: Int, val count: Int) : BottomBarTitle
    }
}

// mftlrs: one-time events of the main screen, executed by FileListFragment.
internal sealed interface FileListEvent {
    data class ScrollTo(val position: ScrollPosition) : FileListEvent

    data class ShowError(val text: String) : FileListEvent // a toast, when there are files already

    data class ShowToast(@StringRes val textRes: Int) : FileListEvent

    // The file name field selects the name without the extension.
    data class SetCreateFileName(val name: String) : FileListEvent

    data class OpenInNewTask(val path: Path) : FileListEvent

    data class ViewFile(
        val file: FileItem,
        val withChooser: Boolean,
        val files: List<FileItem>, // the list on the screen, for the image viewer
    ) : FileListEvent

    data class InstallApk(val uri: Uri) : FileListEvent

    data class Share(val paths: List<Path>, val mimeTypes: List<MimeType>) : FileListEvent

    data class CopyPath(val path: Path) : FileListEvent

    data class OpenInTerminal(val path: Path) : FileListEvent

    data class CreateShortcut(val path: Path, val mimeType: MimeType) : FileListEvent

    // Finishes the activity with the picked paths.
    data class Pick(val paths: LinkedHashSet<Path>, val pickOptions: PickOptions) : FileListEvent

    data object Finish : FileListEvent
}

// mftlrs: the dialogs of the main screen in Compose (FileListDialogs.kt), were DialogFragments.
internal sealed interface FileListDialog {
    data class Properties(val file: FileItem) : FileListDialog // FilePropertiesDialog


    data class NavigateToPath(val path: Path) : FileListDialog

    data class ConfirmReplaceFile(val file: FileItem) : FileListDialog

    data class ConfirmDeleteFiles(val files: FileItemSet) : FileListDialog

    data class CreateArchive(val files: FileItemSet) : FileListDialog

    data class RenameFile(val file: FileItem) : FileListDialog

    data class OpenApk(val file: FileItem) : FileListDialog

    data object CreateFile : FileListDialog

    data object CreateDirectory : FileListDialog
}

// mftlrs: the rationale dialogs of the permission requests, were ShowRequest*DialogFragment. The
// result goes to FileListFragment, which has the permission launchers.
internal enum class FileListPermissionDialog(@StringRes val messageRes: Int, @StringRes val confirmRes: Int) {
    ALL_FILES_ACCESS(R.string.all_files_access_rationale_message, android.R.string.ok),
    STORAGE(R.string.storage_permission_rationale_message, android.R.string.ok),
    STORAGE_IN_SETTINGS(R.string.storage_permission_permanently_denied_message, R.string.open_settings),
    NOTIFICATION(R.string.notification_permission_rationale_message, android.R.string.ok),
    NOTIFICATION_IN_SETTINGS(R.string.notification_permission_permanently_denied_message, R.string.open_settings),
}
