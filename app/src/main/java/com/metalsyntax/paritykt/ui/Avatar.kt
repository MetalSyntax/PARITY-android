package com.metalsyntax.paritykt.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import java.io.File

/** Foto de perfil: solo en el teléfono (filesDir/avatar.jpg, 512 px), no viaja en los respaldos para no inflarlos. */
object AvatarStore {
    var version by mutableIntStateOf(0); private set
    fun file(ctx: Context) = File(ctx.filesDir, "avatar.jpg")

    fun save(ctx: Context, uri: Uri): Boolean {
        val b = ReceiptOcr.load(ctx, uri, 1024) ?: return false
        val side = minOf(b.width, b.height)
        val sq = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side).let { Bitmap.createScaledBitmap(it, 512, 512, true) }
        file(ctx).outputStream().use { sq.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        version++
        return true
    }

    fun clear(ctx: Context) { file(ctx).delete(); version++ }
}

/** Foto de perfil o, si no hay, las iniciales; cambia con un fundido. */
@Composable
fun Avatar(name: String, size: Dp, modifier: Modifier = Modifier, shape: Shape = CircleShape, bg: Color = Surface1, ink: Color = Ink,
           style: TextStyle = MaterialTheme.typography.titleMedium) {
    val ctx = LocalContext.current
    val img = remember(AvatarStore.version) { AvatarStore.file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    Box(modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        Crossfade(img, label = "avatar") { b ->
            if (b != null) Image(b, null, Modifier.size(size), contentScale = ContentScale.Crop)
            else Box(Modifier.size(size), contentAlignment = Alignment.Center) { Text(initials(name), color = ink, style = style) }
        }
    }
}
