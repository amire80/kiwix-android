/*
 * Kiwix Android
 * Copyright (c) 2020 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.kiwix.kiwixmobile.nav.destination.library

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import eu.mhutti1.utils.storage.Bytes
import eu.mhutti1.utils.storage.StorageDevice
import eu.mhutti1.utils.storage.StorageSelectDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kiwix.kiwixmobile.R
import org.kiwix.kiwixmobile.cachedComponent
import org.kiwix.kiwixmobile.core.R.string
import org.kiwix.kiwixmobile.core.base.BaseActivity
import org.kiwix.kiwixmobile.core.base.BaseFragment
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.isManageExternalStoragePermissionGranted
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.navigate
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.viewModel
import org.kiwix.kiwixmobile.core.extensions.browserIntent
import org.kiwix.kiwixmobile.core.extensions.coreMainActivity
import org.kiwix.kiwixmobile.core.extensions.setBottomMarginToFragmentContainerView
import org.kiwix.kiwixmobile.core.extensions.snack
import org.kiwix.kiwixmobile.core.extensions.toast
import org.kiwix.kiwixmobile.core.main.CoreMainActivity
import org.kiwix.kiwixmobile.core.main.KIWIX_APK_WEBSITE_URL
import org.kiwix.kiwixmobile.core.main.MainRepositoryActions
import org.kiwix.kiwixmobile.core.navigateToAppSettings
import org.kiwix.kiwixmobile.core.navigateToSettings
import org.kiwix.kiwixmobile.core.reader.ZimFileReader
import org.kiwix.kiwixmobile.core.reader.ZimReaderSource
import org.kiwix.kiwixmobile.core.utils.EXTERNAL_SELECT_POSITION
import org.kiwix.kiwixmobile.core.utils.INTERNAL_SELECT_POSITION
import org.kiwix.kiwixmobile.core.utils.LanguageUtils
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.core.utils.SimpleRecyclerViewScrollListener
import org.kiwix.kiwixmobile.core.utils.SimpleRecyclerViewScrollListener.Companion.SCROLL_DOWN
import org.kiwix.kiwixmobile.core.utils.SimpleRecyclerViewScrollListener.Companion.SCROLL_UP
import org.kiwix.kiwixmobile.core.utils.dialog.DialogShower
import org.kiwix.kiwixmobile.core.utils.dialog.KiwixDialog
import org.kiwix.kiwixmobile.core.utils.files.FileUtils
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.adapter.BookOnDiskDelegate
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.adapter.BooksOnDiskAdapter
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.adapter.BooksOnDiskListItem
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.adapter.BooksOnDiskListItem.BookOnDisk
import org.kiwix.kiwixmobile.databinding.FragmentDestinationLibraryBinding
import org.kiwix.kiwixmobile.zimManager.MAX_PROGRESS
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.RequestDeleteMultiSelection
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.RequestMultiSelection
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.RequestNavigateTo
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.RequestSelect
import org.kiwix.kiwixmobile.zimManager.fileselectView.FileSelectListState
import java.io.File
import java.util.Locale
import javax.inject.Inject

private const val WAS_IN_ACTION_MODE = "WAS_IN_ACTION_MODE"
private const val MATERIAL_BOTTOM_VIEW_ENTER_ANIMATION_DURATION = 225L

class LocalLibraryFragment : BaseFragment(), CopyMoveFileHandler.FileCopyMoveCallback {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  @Inject lateinit var sharedPreferenceUtil: SharedPreferenceUtil
  @Inject lateinit var dialogShower: DialogShower
  @Inject lateinit var mainRepositoryActions: MainRepositoryActions
  @Inject lateinit var zimReaderFactory: ZimFileReader.Factory

  @JvmField
  @Inject
  var copyMoveFileHandler: CopyMoveFileHandler? = null

  private var actionMode: ActionMode? = null
  private val disposable = CompositeDisposable()
  private var fragmentDestinationLibraryBinding: FragmentDestinationLibraryBinding? = null
  private var permissionDeniedLayoutShowing = false
  private var fileSelectListState: FileSelectListState? = null

  private val zimManageViewModel by lazy {
    requireActivity().viewModel<ZimManageViewModel>(viewModelFactory)
  }

  private var storagePermissionLauncher: ActivityResultLauncher<Array<String>>? =
    registerForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionResult ->
      val isGranted =
        permissionResult.entries.all(
          Map.Entry<String, @kotlin.jvm.JvmSuppressWildcards Boolean>::value
        )
      if (readStorageHasBeenPermanentlyDenied(isGranted)) {
        fragmentDestinationLibraryBinding?.apply {
          permissionDeniedLayoutShowing = true
          fileManagementNoFiles.visibility = VISIBLE
          goToDownloadsButtonNoFiles.visibility = VISIBLE
          fileManagementNoFiles.text =
            requireActivity().resources.getString(string.grant_read_storage_permission)
          goToDownloadsButtonNoFiles.text =
            requireActivity().resources.getString(string.go_to_settings_label)
          zimfilelist.visibility = GONE
        }
      } else if (isGranted) {
        permissionDeniedLayoutShowing = false
      }
    }

  private val bookDelegate: BookOnDiskDelegate.BookDelegate by lazy {
    BookOnDiskDelegate.BookDelegate(
      sharedPreferenceUtil,
      {
        if (!requireActivity().isManageExternalStoragePermissionGranted(sharedPreferenceUtil)) {
          showManageExternalStoragePermissionDialog()
        } else {
          offerAction(RequestNavigateTo(it))
        }
      },
      {
        if (!requireActivity().isManageExternalStoragePermissionGranted(sharedPreferenceUtil)) {
          showManageExternalStoragePermissionDialog()
        } else {
          offerAction(RequestMultiSelection(it))
        }
      },
      { offerAction(RequestSelect(it)) }
    )
  }
  private val booksOnDiskAdapter: BooksOnDiskAdapter by lazy {
    BooksOnDiskAdapter(bookDelegate, BookOnDiskDelegate.LanguageDelegate)
  }

  override fun inject(baseActivity: BaseActivity) {
    baseActivity.cachedComponent.inject(this)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    LanguageUtils(requireActivity())
      .changeFont(requireActivity(), sharedPreferenceUtil)
    fragmentDestinationLibraryBinding = FragmentDestinationLibraryBinding.inflate(
      inflater,
      container,
      false
    )
    val toolbar = fragmentDestinationLibraryBinding?.root?.findViewById<Toolbar>(R.id.toolbar)
    val activity = activity as CoreMainActivity
    activity.setSupportActionBar(toolbar)
    activity.supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle(string.library)
    }
    if (toolbar != null) {
      activity.setupDrawerToggle(toolbar)
    }
    setupMenu()

    return fragmentDestinationLibraryBinding?.root
  }

  private fun setupMenu() {
    (requireActivity() as MenuHost).addMenuProvider(
      object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
          menuInflater.inflate(R.menu.menu_zim_manager, menu)
          val searchItem = menu.findItem(R.id.action_search)
          val languageItem = menu.findItem(R.id.select_language)
          languageItem.isVisible = false
          searchItem.isVisible = false
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
          when (menuItem.itemId) {
            R.id.get_zim_nearby_device -> {
              navigateToLocalFileTransferFragment()
              return true
            }
          }
          return false
        }
      },
      viewLifecycleOwner,
      Lifecycle.State.RESUMED
    )
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpSwipeRefreshLayout()
    copyMoveFileHandler?.apply {
      setFileCopyMoveCallback(this@LocalLibraryFragment)
      setLifeCycleScope(lifecycleScope)
    }
    fragmentDestinationLibraryBinding?.zimfilelist?.run {
      adapter = booksOnDiskAdapter
      layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
      setHasFixedSize(true)
      visibility = GONE
    }
    zimManageViewModel.fileSelectListStates.observe(viewLifecycleOwner, Observer(::render))
      .also {
        coreMainActivity.navHostContainer
          .setBottomMarginToFragmentContainerView(0)

        getBottomNavigationView()?.let {
          setBottomMarginToSwipeRefreshLayout(it.measuredHeight)
        }
      }
    disposable.add(sideEffects())
    disposable.add(fileSelectActions())
    zimManageViewModel.deviceListScanningProgress.observe(viewLifecycleOwner) {
      fragmentDestinationLibraryBinding?.scanningProgressView?.apply {
        progress = it
        // hide this progress bar when scanning is complete.
        visibility = if (it == MAX_PROGRESS) GONE else VISIBLE
        // enable if the previous scanning is completes.
        fragmentDestinationLibraryBinding?.zimSwiperefresh?.isEnabled = it == MAX_PROGRESS
      }
    }
    if (savedInstanceState != null && savedInstanceState.getBoolean(WAS_IN_ACTION_MODE)) {
      zimManageViewModel.fileSelectActions.offer(FileSelectActions.RestartActionMode)
    }

    fragmentDestinationLibraryBinding?.goToDownloadsButtonNoFiles?.setOnClickListener {
      if (permissionDeniedLayoutShowing) {
        permissionDeniedLayoutShowing = false
        requireActivity().navigateToAppSettings()
      } else {
        offerAction(FileSelectActions.UserClickedDownloadBooksButton)
      }
    }
    setUpFilePickerButton()

    fragmentDestinationLibraryBinding?.zimfilelist?.addOnScrollListener(
      SimpleRecyclerViewScrollListener { _, newState ->
        when (newState) {
          SCROLL_DOWN -> {
            setBottomMarginToSwipeRefreshLayout(0)
          }

          SCROLL_UP -> {
            getBottomNavigationView()?.let {
              setBottomMarginToSwipeRefreshLayout(it.measuredHeight)
            }
          }
        }
      }
    )
  }

  private fun setUpSwipeRefreshLayout() {
    fragmentDestinationLibraryBinding?.zimSwiperefresh?.setOnRefreshListener {
      if (permissionDeniedLayoutShowing) {
        fragmentDestinationLibraryBinding?.zimSwiperefresh?.isRefreshing = false
      } else {
        if (!requireActivity().isManageExternalStoragePermissionGranted(sharedPreferenceUtil)) {
          showManageExternalStoragePermissionDialog()
          // Set loading to false since the dialog is currently being displayed.
          // If the user clicks on "No" in the permission dialog,
          // the loading icon remains visible infinitely.
          fragmentDestinationLibraryBinding?.zimSwiperefresh?.isRefreshing = false
        } else {
          fragmentDestinationLibraryBinding?.zimSwiperefresh?.apply {
            // hide the swipe refreshing because now we are showing the ContentLoadingProgressBar
            // to show the progress of how many files are scanned.
            isRefreshing = false
            // disable the swipe refresh layout until the ongoing scanning will not complete
            // to avoid multiple scanning.
            isEnabled = false
          }
          fragmentDestinationLibraryBinding?.scanningProgressView?.apply {
            visibility = VISIBLE
            progress = 0
          }
          requestFileSystemCheck()
        }
      }
    }
  }

  private fun showManageExternalStoragePermissionDialog() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      dialogShower.show(
        KiwixDialog.ManageExternalFilesPermissionDialog,
        {
          this.activity?.let(FragmentActivity::navigateToSettings)
        }
      )
    }
  }

  private fun getBottomNavigationView() =
    requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav_view)

  private fun setBottomMarginToSwipeRefreshLayout(marginBottom: Int) {
    fragmentDestinationLibraryBinding?.zimSwiperefresh?.apply {
      val params = layoutParams as CoordinatorLayout.LayoutParams?
      params?.bottomMargin = marginBottom
      requestLayout()
    }
  }

  private fun setUpFilePickerButton() {
    fragmentDestinationLibraryBinding?.selectFile?.setOnClickListener {
      if (!requireActivity().isManageExternalStoragePermissionGranted(sharedPreferenceUtil)) {
        showManageExternalStoragePermissionDialog()
      } else {
        showFileChooser()
      }
    }
  }

  private fun showFileChooser() {
    val intent = Intent().apply {
      action = Intent.ACTION_OPEN_DOCUMENT
      type = "*/*"
      addCategory(Intent.CATEGORY_OPENABLE)
    }
    try {
      fileSelectLauncher.launch(Intent.createChooser(intent, "Select a zim file"))
    } catch (ex: ActivityNotFoundException) {
      activity.toast(resources.getString(R.string.no_app_found_to_open), Toast.LENGTH_SHORT)
    }
  }

  private val fileSelectLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let {
          requireActivity().applicationContext.contentResolver.takePersistableUriPermission(
            it,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
              Intent.FLAG_GRANT_WRITE_URI_PERMISSION
          )
          handleSelectedFileUri(it)
        }
      }
    }

  private fun handleSelectedFileUri(uri: Uri) {
    getZimFileFromUri(uri)?.let { file ->
      if (sharedPreferenceUtil.isPlayStoreBuildWithAndroid11OrAbove()) {
        copyMoveFileHandler?.showMoveFileToPublicDirectoryDialog(uri, file)
      } else {
        navigateToReaderFragment(file)
      }
    }
  }

  private fun getZimFileFromUri(
    uri: Uri
  ): File? {
    val filePath = FileUtils.getLocalFilePathByUri(
      requireActivity().applicationContext, uri
    )
    if (filePath == null || !File(filePath).exists()) {
      activity.toast(string.error_file_not_found)
      return null
    }
    val file = File(filePath)
    return if (!FileUtils.isValidZimFile(file.path)) {
      activity.toast(string.error_file_invalid)
      null
    } else {
      file
    }
  }

  private fun navigateToReaderFragment(file: File) {
    if (!file.canRead()) {
      activity.toast(string.unable_to_read_zim_file)
    } else {
      // Save the ZIM file to the database to display it on the local library screen.
      // This is particularly useful when storage is slow or contains a large number of files.
      // In such cases, scanning may take some time to show all the files on the
      // local library screen. Since our application is already aware of this opened ZIM file,
      // we can directly add it to the database.
      // See https://github.com/kiwix/kiwix-android/issues/3650
      CoroutineScope(Dispatchers.IO).launch {
        zimReaderFactory.create(ZimReaderSource(file))
          ?.let { zimFileReader ->
            BookOnDisk(zimFileReader).also {
              mainRepositoryActions.saveBook(it)
              zimFileReader.dispose()
            }
          }
      }
      activity?.navigate(
        LocalLibraryFragmentDirections.actionNavigationLibraryToNavigationReader()
          .apply { zimFileUri = file.toUri().toString() }
      )
    }
  }

  override fun onResume() {
    super.onResume()
    if (sharedPreferenceUtil.isPlayStoreBuildWithAndroid11OrAbove() &&
      sharedPreferenceUtil.playStoreRestrictionPermissionDialog
    ) {
      showPlayStoreRestrictionInformationToUser()
    } else if (!sharedPreferenceUtil.isPlayStoreBuildWithAndroid11OrAbove() &&
      !sharedPreferenceUtil.prefIsTest && !permissionDeniedLayoutShowing
    ) {
      checkPermissions()
    } else if (!permissionDeniedLayoutShowing) {
      fragmentDestinationLibraryBinding?.zimfilelist?.visibility = VISIBLE
    }
  }

  private fun showPlayStoreRestrictionInformationToUser() {
    // We should only ask for first time
    sharedPreferenceUtil.playStoreRestrictionPermissionDialog = false
    // Show Dialog to the user to inform about the play store restriction
    dialogShower.show(
      KiwixDialog.PlayStoreRestrictionPopup(KIWIX_APK_WEBSITE_URL),
      {},
      ::openKiwixWebsiteForDownloadingApk
    )
  }

  private fun openKiwixWebsiteForDownloadingApk() {
    requireActivity().startActivity(KIWIX_APK_WEBSITE_URL.toUri().browserIntent())
  }

  override fun onDestroyView() {
    super.onDestroyView()
    mainRepositoryActions.dispose()
    actionMode = null
    fragmentDestinationLibraryBinding?.zimfilelist?.adapter = null
    fragmentDestinationLibraryBinding = null
    disposable.clear()
    storagePermissionLauncher?.unregister()
    storagePermissionLauncher = null
    copyMoveFileHandler?.dispose()
    copyMoveFileHandler = null
  }

  private fun sideEffects() = zimManageViewModel.sideEffects
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
      {
        val effectResult = it.invokeWith(requireActivity() as AppCompatActivity)
        if (effectResult is ActionMode) {
          actionMode = effectResult
          fileSelectListState?.selectedBooks?.size?.let(::setActionModeTitle)
        }
      }, Throwable::printStackTrace
    )

  private fun fileSelectActions() = zimManageViewModel.fileSelectActions
    .observeOn(AndroidSchedulers.mainThread())
    .filter { it === RequestDeleteMultiSelection }
    .subscribe(
      {
        animateBottomViewToOrigin()
      },
      Throwable::printStackTrace
    )

  private fun animateBottomViewToOrigin() {
    getBottomNavigationView().animate()
      .translationY(0F)
      .setDuration(MATERIAL_BOTTOM_VIEW_ENTER_ANIMATION_DURATION)
      .start()
  }

  private fun render(state: FileSelectListState) {
    fileSelectListState = state
    val items: List<BooksOnDiskListItem> = state.bookOnDiskListItems
    bookDelegate.selectionMode = state.selectionMode
    booksOnDiskAdapter.items = items
    if (items.none(BooksOnDiskListItem::isSelected)) {
      actionMode?.finish()
      actionMode = null
    }
    setActionModeTitle(state.selectedBooks.size)
    fragmentDestinationLibraryBinding?.apply {
      if (items.isEmpty()) {
        fileManagementNoFiles.text = requireActivity().resources.getString(string.no_files_here)
        goToDownloadsButtonNoFiles.text =
          requireActivity().resources.getString(string.download_books)

        fileManagementNoFiles.visibility = View.VISIBLE
        goToDownloadsButtonNoFiles.visibility = View.VISIBLE
        zimfilelist.visibility = View.GONE
      } else {
        fileManagementNoFiles.visibility = View.GONE
        goToDownloadsButtonNoFiles.visibility = View.GONE
        zimfilelist.visibility = View.VISIBLE
      }
    }
  }

  private fun setActionModeTitle(selectedBookCount: Int) {
    actionMode?.title = String.format(Locale.getDefault(), "%d", selectedBookCount)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(WAS_IN_ACTION_MODE, actionMode != null)
  }

  private fun checkPermissions() {
    if (ContextCompat.checkSelfPermission(
        requireActivity(),
        Manifest.permission.READ_EXTERNAL_STORAGE
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        context.toast(string.request_storage)
        storagePermissionLauncher?.launch(
          arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
          )
        )
      } else {
        checkManageExternalStoragePermission()
      }
    } else {
      checkManageExternalStoragePermission()
    }
  }

  private fun checkManageExternalStoragePermission() {
    if (!sharedPreferenceUtil.isPlayStoreBuild && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        // We do not have the permission!!
        if (sharedPreferenceUtil.manageExternalFilesPermissionDialog) {
          // We should only ask for first time, If the users wants to revoke settings
          // then they can directly toggle this feature from settings screen
          sharedPreferenceUtil.manageExternalFilesPermissionDialog = false
          // Show Dialog and  Go to settings to give permission
          showManageExternalStoragePermissionDialog()
        }
      }
    }
  }

  private fun requestFileSystemCheck() {
    zimManageViewModel.requestFileSystemCheck.onNext(Unit)
  }

  private fun offerAction(action: FileSelectActions) {
    zimManageViewModel.fileSelectActions.offer(action)
  }

  private fun navigateToLocalFileTransferFragment() {
    requireActivity().navigate(R.id.localFileTransferFragment)
  }

  private fun shouldShowRationalePermission() =
    ActivityCompat.shouldShowRequestPermissionRationale(
      requireActivity(),
      Manifest.permission.READ_EXTERNAL_STORAGE
    )

  private fun readStorageHasBeenPermanentlyDenied(isPermissionGranted: Boolean) =
    !isPermissionGranted &&
      !shouldShowRationalePermission()

  override fun onFileCopied(file: File) {
    navigateToReaderFragment(file = file)
  }

  override fun onFileMoved(file: File) {
    navigateToReaderFragment(file = file)
  }

  override fun onError(errorMessage: String) {
    activity.toast(errorMessage)
  }

  override fun filesystemDoesNotSupportedCopyMoveFilesOver4GB() {
    showStorageSelectionSnackBar(getString(R.string.file_system_does_not_support_4gb))
  }

  override fun insufficientSpaceInStorage(availableSpace: Long) {
    val message = """
        ${getString(string.move_no_space)}
        ${getString(string.space_available)} ${Bytes(availableSpace).humanReadable}
    """.trimIndent()

    showStorageSelectionSnackBar(message)
  }

  private fun showStorageSelectionSnackBar(message: String) {
    fragmentDestinationLibraryBinding?.zimfilelist?.snack(
      message,
      requireActivity().findViewById(R.id.bottom_nav_view),
      string.download_change_storage,
      ::showStorageSelectDialog
    )
  }

  private fun showStorageSelectDialog() = StorageSelectDialog()
    .apply {
      onSelectAction = ::storeDeviceInPreferences
    }
    .show(parentFragmentManager, getString(string.pref_storage))

  private fun storeDeviceInPreferences(
    storageDevice: StorageDevice
  ) {
    sharedPreferenceUtil.putPrefStorage(
      sharedPreferenceUtil.getPublicDirectoryPath(storageDevice.name)
    )
    sharedPreferenceUtil.putStoragePosition(
      if (storageDevice.isInternal) INTERNAL_SELECT_POSITION
      else EXTERNAL_SELECT_POSITION
    )
    // after selecting the storage try to copy/move the zim file.
    copyMoveFileHandler?.showMoveFileToPublicDirectoryDialog()
  }
}
