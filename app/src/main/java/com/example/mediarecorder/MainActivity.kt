package com.example.mediarecorder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.IOException

class MainActivity : ComponentActivity() {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording by mutableStateOf(false)
    private var recordingUri: Uri? = null
    private var recordedFiles by mutableStateOf<List<String>>(emptyList())
    private var isLoading by mutableStateOf(true)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                isLoading = false
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                showPermissionSettingsDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()
        getRecordedFiles()

        setContent {
            if (isLoading) {
                CircularProgressIndicator()
            } else
                RecordingApp()
        }
    }

    @Composable
    fun RecordingApp() {
        var status by remember { mutableStateOf("Not Recording") }

        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = {
                if (isRecording) {
                    stopRecording()
                    status = "Recording Stopped"
                } else {
                    startRecording()
                    status = "Recording..."
                }
            }) {
                Text(text = if (isRecording) "Stop Recording" else "Start Recording")
            }
            Spacer(Modifier.height(16.dp))
            Text(text = "Status: $status")

            Spacer(Modifier.height(32.dp))
            Text(text = "Recorded Files:")
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(recordedFiles) { file ->
                    Button(onClick = { playRecording(file) }) { Text(text = file) }
                }
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            isLoading = false
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            showPermissionSettingsDialog()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("권한이 필요합니다")
            .setMessage("마이크 권한을 거부하셨습니다. 설정에서 권한을 허용해주세요.")
            .setPositiveButton("설정으로 가기") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("취소") { _, _ ->
                finish()
            }
            .setOnDismissListener { finish() }
            .show()
    }

    private fun startRecording() {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Recordings/ResQ")
        }

        val contentResolver: ContentResolver = applicationContext.contentResolver
        recordingUri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)

        try {
            @Suppress("DEPRECATION")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(
                    contentResolver.openFileDescriptor(
                        recordingUri!!,
                        "w"
                    )?.fileDescriptor
                )
            }
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            isRecording = true
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        isRecording = false

        getRecordedFiles()
    }

    private fun getRecordedFiles() {
        val contentResolver: ContentResolver = applicationContext.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)

        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%ResQ%")

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )

        val fileList = mutableListOf<String>()
        cursor?.use {
            val displayNameColumn = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            while (it.moveToNext()) {
                val fileName = it.getString(displayNameColumn)
                fileList.add(fileName)
            }
        }

        recordedFiles = fileList
    }

    @SuppressLint("Range")
    private fun playRecording(fileName: String) {
        val contentResolver: ContentResolver = applicationContext.contentResolver
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            null,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val fileUri = it.getString(it.getColumnIndex(MediaStore.MediaColumns.DATA))
                mediaPlayer?.reset()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(fileUri)
                    prepare()
                    start()
                }
            }
        }
    }
}