package github.leavesczy.matisse.internal.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import github.leavesczy.matisse.CaptureStrategy
import github.leavesczy.matisse.MatisseContract
import github.leavesczy.matisse.MediaResource
import github.leavesczy.matisse.internal.logic.MatissePageAction
import github.leavesczy.matisse.internal.logic.MatisseViewModel
import github.leavesczy.matisse.internal.logic.SelectionSpec
import github.leavesczy.matisse.internal.theme.MatisseTheme
import github.leavesczy.matisse.internal.utils.PermissionUtils
import kotlinx.coroutines.launch
import com.smarx.notchlib.NotchScreenManager

/**
 * @Author: leavesCZY
 * @Date: 2022/5/28 22:28
 * @Desc:
 */
class MatisseActivity : ComponentActivity() {

    private val matisse by lazy {
        SelectionSpec.getMatisse()
    }

    private val captureStrategy: CaptureStrategy
        get() = matisse.captureStrategy

    private val matisseViewModel by viewModels<MatisseViewModel>(factoryProducer = {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MatisseViewModel(
                    application = application,
                    matisse = matisse
                ) as T
            }
        }
    })

    private val requestReadExternalStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            matisseViewModel.onRequestReadExternalStoragePermissionResult(granted = granted)
        }

    private val requestWriteExternalStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                requestCameraPermissionIfNeed()
            } else {
                val tips = matisse.tips.onWriteExternalStorageDenied
                if (tips.isNotBlank()) {
                    Toast.makeText(this, tips, Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                takePicture()
            } else {
                val tips = matisse.tips.onCameraDenied
                if (tips.isNotBlank()) {
                    Toast.makeText(this, tips, Toast.LENGTH_SHORT).show()
                }
            }
        }

    private var tempImageUri: Uri? = null

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { result ->
            val mTempImageUri = tempImageUri
            tempImageUri = null
            if (mTempImageUri != null) {
                lifecycleScope.launch {
                    if (result) {
                        val resource = captureStrategy.loadResource(
                            context = this@MatisseActivity,
                            imageUri = mTempImageUri
                        )
                        if (resource != null) {
                            onSure(listOf(resource))
                        }
                    } else {
                        captureStrategy.onTakePictureCanceled(
                            context = this@MatisseActivity,
                            imageUri = mTempImageUri
                        )
                    }
                }
            }
        }

    private val matissePageAction = MatissePageAction(
        onClickBackMenu = {
            finish()
        }, onRequestCapture = {
            onRequestCapture()
        }, onSureButtonClick = {
            onSure(selectedMediaResources = matisseViewModel.selectedMediaResources)
        })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        NotchScreenManager.getInstance().setDisplayInNotch(this)
        setContent {
            MatisseTheme(matisseTheme = matisse.theme) {
                val matisseViewState by matisseViewModel.matisseViewState.collectAsState()
                val matissePreviewViewState by matisseViewModel.matissePreviewViewState.collectAsState()
                SetSystemUi(previewVisible = matissePreviewViewState.visible)
                MatissePage(viewState = matisseViewState, action = matissePageAction)
                MatissePreviewPage(viewState = matissePreviewViewState)
            }
        }
        if (PermissionUtils.checkSelfPermission(
                context = this,
                permission = Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            matisseViewModel.onRequestReadExternalStoragePermissionResult(granted = true)
        } else {
            matisseViewModel.onRequestReadExternalStoragePermission()
            requestReadExternalStoragePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun onRequestCapture() {
        if (captureStrategy.shouldRequestWriteExternalStoragePermission(context = this)) {
            requestWriteExternalStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            requestCameraPermissionIfNeed()
        }
    }

    private fun requestCameraPermissionIfNeed() {
        if (PermissionUtils.containsPermission(
                context = this,
                permission = Manifest.permission.CAMERA
            ) && !PermissionUtils.checkSelfPermission(
                context = this,
                permission = Manifest.permission.CAMERA
            )
        ) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            takePicture()
        }
    }

    private fun takePicture() {
        lifecycleScope.launch {
            tempImageUri = null
            val imageUri = captureStrategy.createImageUri(context = this@MatisseActivity)
            if (imageUri != null) {
                val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (captureIntent.resolveActivity(packageManager) != null) {
                    takePictureLauncher.launch(imageUri)
                    tempImageUri = imageUri
                }
            }
        }
    }

    private fun onSure(selectedMediaResources: List<MediaResource>) {
        if (selectedMediaResources.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
        } else {
            val data = MatisseContract.buildResult(selectedMediaResources = selectedMediaResources)
            setResult(Activity.RESULT_OK, data)
        }
        finish()
    }

}