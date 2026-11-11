package com.creatora.app.ui.render

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.creatora.app.R
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme
import java.io.File

/**
 * Save to gallery and Share for a finished file. Android 10+ saves straight into the gallery; older
 * phones open the system "save as" dialog instead.
 */
@Composable
fun ResultActions(
    fileUri: String,
    displayName: String,
    mimeType: String,
    message: GalleryMessage?,
    onSaveToGallery: () -> Unit,
    onSaveTo: (destinationUri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        uri?.let { onSaveTo(it.toString()) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            SecondaryButton(
                text = stringResource(R.string.render_save),
                onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) onSaveToGallery() else saveAs.launch(displayName) },
            )
            SecondaryButton(
                text = stringResource(R.string.render_share),
                onClick = {
                    val file = File(requireNotNull(Uri.parse(fileUri).path))
                    val shared = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType(mimeType)
                        .putExtra(Intent.EXTRA_STREAM, shared)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, null))
                },
            )
        }
        when (message) {
            GalleryMessage.SAVED -> Text(stringResource(R.string.render_saved), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.success)
            GalleryMessage.FAILED -> Text(stringResource(R.string.render_save_failed), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger)
            null -> Unit
        }
    }
}
