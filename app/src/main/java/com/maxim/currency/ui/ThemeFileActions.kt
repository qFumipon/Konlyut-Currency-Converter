package com.maxim.currency.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.maxim.currency.data.StyleFileCodec
import com.maxim.currency.domain.ThemePreset
import java.io.File
import java.util.UUID

internal fun saveThemeToUri(context: Context, uri: Uri, preset: ThemePreset) {
    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(StyleFileCodec.encode(preset)) }
        ?: error("Could not open destination")
}

internal fun shareThemeFile(context: Context, preset: ThemePreset, chooserTitle: String) {
    val directory = File(context.cacheDir, "shared_themes").apply { mkdirs() }
    val name=StyleFileCodec.suggestedFileName(preset.name).removeSuffix(StyleFileCodec.EXTENSION)
    val file = File(directory, "$name-${UUID.randomUUID().toString().take(8)}${StyleFileCodec.EXTENSION}")
    file.writeBytes(StyleFileCodec.encode(preset))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = StyleFileCodec.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
