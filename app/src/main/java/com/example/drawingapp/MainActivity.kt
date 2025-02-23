package com.example.drawingapp

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.Image
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.example.drawingapp.ui.theme.DrawingAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : ComponentActivity() {

    var customProgressDialog: Dialog? = null


    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null

    val openGalleryLauncher :ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    {
        result->
          if(result.resultCode == RESULT_OK  && result.data!=null)
          {
              val imageBackground : ImageView =findViewById(R.id.iv_background)

              imageBackground.setImageURI(result.data?.data)
          }
    }

    val requestPermission :ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
    {
        permissions ->
        permissions.entries.forEach{
            val permissionName= it.key
            val isGranted = it.value

            if(isGranted)
            {
                Toast.makeText(this,"Permission granted now you can read the Storage files.",Toast.LENGTH_LONG).show()
                val pickIntent =Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            }
            else
            {
                if(permissionName== android.Manifest.permission.READ_EXTERNAL_STORAGE)
                {
                    Toast.makeText(this,"Permission denied for reading external Storage files", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_normal)
        )

        val ib_brush: ImageButton = findViewById(R.id.ib_brush)
        ib_brush.setOnClickListener {
            showBrushSizeChooserDialog()
        }


        val ib_undo: ImageButton = findViewById(R.id.ib_undo)
        ib_undo.setOnClickListener {
            drawingView?.onclickUndoPath()
        }

        val ib_redo: ImageButton = findViewById(R.id.ib_redo)
        ib_redo.setOnClickListener {
            drawingView?.onclickRedoPath()
        }

        val ib_save :ImageButton =findViewById(R.id.ib_save)
        ib_save.setOnClickListener {

            if(isReadStorageAllowed())
            {
                showProgressDialog()
                lifecycleScope.launch {
                    val flDrawingView : FrameLayout= findViewById(R.id.fl_drawing_view_container)

                    saveBitmapFile(getBitmapfromView(flDrawingView))

                }
            }


        }

        val ib_gallery : ImageButton = findViewById(R.id.ib_gallery)
        ib_gallery.setOnClickListener()
        {
            requestStoragePermission()
        }



    }

    private fun showBrushSizeChooserDialog() {



        val brushDialog = Dialog(this@MainActivity)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size ")



        val smallBtn: ImageButton? = brushDialog.findViewById(R.id.small_brush)
        if (smallBtn == null) {
            Log.e("MainActivity", "small_brush ImageButton not found!")
        } else {
            smallBtn.setOnClickListener {
                Log.d("MainActivity", "smallBtn clicked")
                drawingView?.setSizeForBrush(10.toFloat())
                brushDialog.dismiss()
            }
        }



        val mediumBtn: ImageButton ?= brushDialog.findViewById(R.id.medium_brush)
        mediumBtn?.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn: ImageButton ?= brushDialog.findViewById(R.id.large_brush)
        largeBtn?.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()

    }


    private fun showProgressDialog() {
        customProgressDialog = Dialog(this)
        customProgressDialog?.setContentView(R.layout.custom_progress_bar)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog() {
        if (customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null

        }
    }

    private fun shareImage (result : String)
    {

        MediaScannerConnection.scanFile(this, arrayOf(result),null)
        {
            path, uri->
            val shareIntent =Intent()
            shareIntent.action =Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type="image/png"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }



     fun paintClicked(view: View) {
        if (view != mImageButtonCurrentPaint) {
            val ImageButton = view as ImageButton
            val colorTag = ImageButton.tag.toString()
            try {
                drawingView?.setColor(colorTag)

            } catch (e: IllegalArgumentException) {
                Log.e("PaintClicked", "Invalid color tag: $colorTag", e)
                // Optional: Provide a fallback or inform the user
            }


            ImageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallete_press)
            )


            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )

            mImageButtonCurrentPaint = view


        }
    }


    private fun getBitmapfromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        }
//        else
//        {
//            canvas.drawColor(Color.White)
//        }

        view.draw(canvas)

        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO)
        {
            if (mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)


                    val f = File(
                        externalCacheDir?.absoluteFile.toString()
                                + File.separator + "DrawingApp_" + System.currentTimeMillis() / 1000 + ".png"
                    )

                    val fo= FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread{
                        cancelProgressDialog()
                        if(result != null)
                        {
                            Toast.makeText(this@MainActivity,"File Saved Successfully : ${result}",Toast.LENGTH_SHORT).show()
                        }
                        shareImage(result)

                        run {
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong.File not saved",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                }
                catch (e: Exception)
                {
                    result=""
                    e.printStackTrace()
                }
            }
        }
        return result

    }
    private fun isReadStorageAllowed() : Boolean
    {
        val result= ContextCompat.checkSelfPermission(this,
            android.Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED

    }

    private fun requestStoragePermission()
    {
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_EXTERNAL_STORAGE))
            {
            showRationaleDialog("Drawing App","Drawing app needs to access your External Storage")
            }
            else
        {
            requestPermission.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        }

    }

    private fun showRationaleDialog(title: String, message: String)
    {
        val builder= AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Cancel")
        { dialog,which->
            dialog.dismiss()
        }
    }
}

