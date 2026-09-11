package dev.ykro.trailaid.ui.emergency

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat

/** Stretch feature: photograph the injury and let the same on-device agent pick the protocol. */
@Composable
fun PhotoCaptureDialog(onDismiss: () -> Unit, onCaptured: (Bitmap) -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Injury photo", style = MaterialTheme.typography.titleLarge)
        Text("Stays on the phone. The agent will suggest a protocol from what it sees.", style = MaterialTheme.typography.bodyMedium)
        AndroidView(
          factory = { ctx ->
            PreviewView(ctx).also { view ->
              val future = ProcessCameraProvider.getInstance(ctx)
              future.addListener(
                {
                  val provider = future.get()
                  val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                  provider.unbindAll()
                  runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture) }
                    .onFailure { runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture) } }
                },
                ContextCompat.getMainExecutor(ctx),
              )
            }
          },
          modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(16.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
          Button(
            onClick = {
              imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                  override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    onCaptured(if (rotation != 0) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true) else bitmap)
                  }

                  override fun onError(exception: ImageCaptureException) = onDismiss()
                },
              )
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("Take photo")
          }
        }
      }
    }
  }
}
