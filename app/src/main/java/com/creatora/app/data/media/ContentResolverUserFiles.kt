package com.creatora.app.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.creatora.app.domain.model.PickedFile
import com.creatora.app.domain.repository.UserFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentResolverUserFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserFiles {
    private val dir = File(context.filesDir, "imports")

    override suspend fun import(uri: String): PickedFile? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val source = Uri.parse(uri)
        val name = runCatching {
            resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: source.lastPathSegment ?: return@withContext null
        dir.mkdirs()
        val target = File(dir, "${UUID.randomUUID()}_${name.replace('/', '_')}")
        val copied = runCatching {
            resolver.openInputStream(source)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        }.isSuccess
        if (!copied) {
            target.delete()
            return@withContext null
        }
        PickedFile(Uri.fromFile(target).toString(), name, resolver.getType(source).orEmpty())
    }

    override suspend fun discard(uri: String) {
        withContext(Dispatchers.IO) {
            val file = Uri.parse(uri).path?.let(::File) ?: return@withContext
            if (file.parentFile?.canonicalPath == dir.canonicalPath) file.delete()
        }
    }
}
