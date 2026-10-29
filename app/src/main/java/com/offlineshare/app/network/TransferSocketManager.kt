package com.offlineshare.app.network

import android.content.Context
import android.net.Uri
import com.offlineshare.app.model.*
import com.offlineshare.app.security.CryptoEngine
import com.offlineshare.app.storage.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

class TransferSocketManager(private val context: Context) {
    companion object { const val DEFAULT_PORT = 8888; private const val CHUNK = 64 * 1024 }
    private val _progress = MutableSharedFlow<TransferProgress>(replay = 1)
    val progressFlow: SharedFlow<TransferProgress> = _progress
    @Volatile private var paused = false
    @Volatile private var cancelled = false

    fun pauseTransfer() { paused = true }
    fun resumeTransfer() { paused = false }
    fun cancelTransfer() { cancelled = true }
    fun reset() { paused = false; cancelled = false }

    private enum class T(val id:Int){ HANDSHAKE(1), FILE_START(2), DATA(3), FILE_END(4), DONE(5), ERROR(6) }
    private fun sendFrame(out: DataOutputStream, type:T, plain:ByteArray, key:javax.crypto.spec.SecretKeySpec) {
        val enc = CryptoEngine.encryptChunk(plain, 0, plain.size, key)
        out.writeInt(type.id); out.writeInt(enc.size); out.write(enc); out.flush()
    }
    private fun readFrame(input:DataInputStream, key:javax.crypto.spec.SecretKeySpec):Pair<T,ByteArray> {
        val id=input.readInt(); val len=input.readInt(); require(len in 1..(CHUNK+8192))
        val b=ByteArray(len); input.readFully(b); return T.values().first { it.id==id } to CryptoEngine.decryptChunk(b,0,b.size,key)
    }

    suspend fun sendFiles(host:String, files:List<TransferItem>, pin:String):Boolean = withContext(Dispatchers.IO) {
        reset(); val key=CryptoEngine.deriveKey(pin,"OfflineShare_Salt_V1".toByteArray())
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host,DEFAULT_PORT),10000)
            DataOutputStream(BufferedOutputStream(socket.getOutputStream(),128*1024)).use { out ->
                DataInputStream(BufferedInputStream(socket.getInputStream())).use { input ->
                    val hs=JSONObject().apply{put("fileCount",files.size);put("totalBytes",files.sumOf{it.size})}
                    sendFrame(out,T.HANDSHAKE,hs.toString().toByteArray(),key)
                    for ((idx,item) in files.withIndex()) {
                        if(cancelled) break
                        sendFrame(out,T.FILE_START,JSONObject().apply{put("name",item.name);put("size",item.size);put("mime",item.mimeType)}.toString().toByteArray(),key)
                        context.contentResolver.openInputStream(Uri.parse(item.uriString)).use { stream ->
                            requireNotNull(stream)
                            val digest=MessageDigest.getInstance("SHA-256"); val buf=ByteArray(CHUNK); var done=0L; var n:Int
                            while(stream.read(buf).also{n=it}>0){ while(paused && !cancelled) Thread.sleep(150); if(cancelled) break; digest.update(buf,0,n); sendFrame(out,T.DATA,buf.copyOf(n),key); done+=n; _progress.emit(TransferProgress(item.name,done,item.size,idx,files.size)) }
                            sendFrame(out,T.FILE_END,digest.digest().toHex().toByteArray(),key)
                        }
                    }
                    sendFrame(out,T.DONE,ByteArray(0),key)
                    true
                }
            }
        }
    }

    suspend fun receiveFiles(pin:String, onFinished:(Int)->Unit = {}) = withContext(Dispatchers.IO) {
        reset(); val key=CryptoEngine.deriveKey(pin,"OfflineShare_Salt_V1".toByteArray())
        ServerSocket(DEFAULT_PORT).use { server -> server.reuseAddress=true; server.accept().use { socket ->
            DataInputStream(BufferedInputStream(socket.getInputStream(),128*1024)).use { input ->
                var index=0; var current:File?=null; var out:FileOutputStream?=null; var meta:JSONObject?=null; var written=0L; var digest:MessageDigest?=null; var name=""; var mime="application/octet-stream"; var size=0L
                while(true){
                    val (type,payload)=readFrame(input,key)
                    when(type){
                        T.HANDSHAKE -> { JSONObject(String(payload)); }
                        T.FILE_START -> { meta=JSONObject(String(payload)); name=meta!!.getString("name"); size=meta!!.getLong("size"); mime=meta!!.getString("mime"); current=File(context.cacheDir,"recv_${System.currentTimeMillis()}_${name.replace("/","_")}"); out=FileOutputStream(current); digest=MessageDigest.getInstance("SHA-256"); written=0; }
                        T.DATA -> { out?.write(payload); digest?.update(payload); written+=payload.size; _progress.emit(TransferProgress(name,written,size,index,0)) }
                        T.FILE_END -> { out?.close(); val expected=String(payload); val actual=digest!!.digest().toHex(); if(expected!=actual) throw IOException("Checksum mismatch for $name"); current?.let{MediaStoreHelper.saveFileToMediaStore(context,it,name,mime,MediaStoreHelper.classifyFile(mime,name));it.delete()}; index++ }
                        T.DONE -> { onFinished(index); break }
                        T.ERROR -> throw IOException(String(payload))
                    }
                }
            }
        }}
    }
    private fun ByteArray.toHex()=joinToString(""){ "%02x".format(it) }
}
