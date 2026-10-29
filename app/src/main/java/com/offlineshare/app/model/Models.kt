package com.offlineshare.app.model

enum class FileCategory { PHOTOS, VIDEOS, AUDIO, APKS, DOCUMENTS }

data class TransferItem(val uriString: String, val name: String, val size: Long, val mimeType: String)
data class PeerDevice(val id: String, val name: String, val address: String, val port: Int, val isGroupOwner: Boolean)
data class TransferProgress(val fileName: String, val bytesTransferred: Long, val totalBytes: Long, val fileIndex: Int, val fileCount: Int)
