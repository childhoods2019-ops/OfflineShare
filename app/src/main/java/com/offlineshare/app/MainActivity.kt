package com.offlineshare.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.offlineshare.app.model.TransferItem
import com.offlineshare.app.network.TransferSocketManager
import com.offlineshare.app.network.WifiP2pController
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity: ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState); requestPermissionsIfNeeded(); setContent{OfflineShareTheme{OfflineShareScreen()}}}
    private fun requestPermissionsIfNeeded(){
        val p=mutableListOf<String>()
        if(android.os.Build.VERSION.SDK_INT>=33) p+=Manifest.permission.NEARBY_WIFI_DEVICES else p+=Manifest.permission.ACCESS_FINE_LOCATION
        if(android.os.Build.VERSION.SDK_INT>=33) p+=Manifest.permission.POST_NOTIFICATIONS
        if(android.os.Build.VERSION.SDK_INT>=33){p+=Manifest.permission.READ_MEDIA_IMAGES;p+=Manifest.permission.READ_MEDIA_VIDEO;p+=Manifest.permission.READ_MEDIA_AUDIO}else p+=Manifest.permission.READ_EXTERNAL_STORAGE
        if(p.any{ActivityCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED}) ActivityCompat.requestPermissions(this,p.toTypedArray(),42)
    }
}

@Composable fun OfflineShareTheme(content:@Composable()->Unit){ MaterialTheme(colorScheme=lightColorScheme(),content=content) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OfflineShareScreen(){
    val context=LocalContext.current; val scope=rememberCoroutineScope(); val wifi=remember{WifiP2pController(context)}; val transfer=remember{TransferSocketManager(context)}
    var mode by remember{mutableStateOf("home")}; var pin by remember{mutableStateOf("123456")}; var status by remember{mutableStateOf("Ready — no internet required")}; var selected by remember{mutableStateOf<List<TransferItem>>(emptyList())}; var progress by remember{mutableStateOf(0f)}
    val peers by wifi.discoveredPeers.collectAsState(); val info by wifi.connectionInfo.collectAsState()
    DisposableEffect(Unit){wifi.register(); onDispose{}}
    LaunchedEffect(Unit){transfer.progressFlow.collect{p->progress=if(p.totalBytes>0)p.bytesTransferred.toFloat()/p.totalBytes else 0f; status="Transferring ${p.fileName} • ${(progress*100).toInt()}%"}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        selected=uris.mapNotNull{u->
            val cr=context.contentResolver; val c=cr.query(u,arrayOf("_display_name","_size","mime_type"),null,null,null); c?.use{if(it.moveToFirst()){TransferItem(u.toString(),it.getString(0)?:"file",it.getLong(1).coerceAtLeast(0),it.getString(2)?:(cr.getType(u)?:"application/octet-stream"))}else null}
        }; if(selected.isNotEmpty())status="${selected.size} file(s) selected" }
    Scaffold(topBar={TopAppBar(title={Text("OfflineShare",fontWeight=FontWeight.Bold)},actions={Icon(Icons.Default.WifiOff,null)})}){pad->
        Column(Modifier.padding(pad).padding(20.dp).fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){
            Text("Direct phone-to-phone transfer",style=MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Text(status)
            Spacer(Modifier.height(24.dp))
            when(mode){
                "home"->{
                    Button({mode="receive"; status="Creating offline network…"; wifi.createAutonomousGroup({scope.launch{status="Waiting for sender…"; try{transfer.receiveFiles(pin){status="Transfer complete"}}catch(e:Exception){status="Receive failed: ${e.message}"}}},{status="Could not create Wi-Fi Direct group"})},Modifier.fillMaxWidth().height(52.dp)){Icon(Icons.Default.Download,null);Spacer(Modifier.width(8.dp));Text("Receive files")}
                    Spacer(Modifier.height(12.dp)); OutlinedButton({mode="send";wifi.startDiscovery();status="Searching for nearby devices…"},Modifier.fillMaxWidth().height(52.dp)){Icon(Icons.Default.Upload,null);Spacer(Modifier.width(8.dp));Text("Send files")}
                    Spacer(Modifier.height(24.dp)); Text("Works without mobile data or internet",style=MaterialTheme.typography.bodyMedium)
                }
                "send"->{
                    OutlinedTextField(pin,{pin=it.take(8)},label={Text("Transfer PIN")},modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp)); Button({picker.launch(arrayOf("*/*"))},Modifier.fillMaxWidth()){Icon(Icons.Default.AttachFile,null);Spacer(Modifier.width(8.dp));Text("Choose files")}
                    if(selected.isNotEmpty()){Spacer(Modifier.height(8.dp));LazyColumn(Modifier.weight(1f)){items(selected){Text("${it.name}  (${formatBytes(it.size)})",modifier=Modifier.padding(6.dp))}}} else Spacer(Modifier.weight(1f))
                    Text("Nearby devices",fontWeight=FontWeight.Bold); Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.weight(.7f)){items(peers){peer->Card(onClick={
                        if(selected.isEmpty()){Toast.makeText(context,"Choose files first",Toast.LENGTH_SHORT).show();return@Card}
                        wifi.connectByAddress(peer.address,onSuccess={status="Connected — press Send now"},onFailure={status="Connection failed ($it)"})
                    }, modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.PhoneAndroid,null);Spacer(Modifier.width(12.dp));Text(peer.name);Spacer(Modifier.weight(1f));Text("Connect")}}}}
                    Button({if(info?.groupOwnerAddress!=null&&selected.isNotEmpty())scope.launch{try{status="Sending…";transfer.sendFiles(info!!.groupOwnerAddress!!.hostAddress,selected,pin);status="Transfer complete"}catch(e:Exception){status="Send failed: ${e.message}"}}else status="Connect to a receiver first and choose files"},Modifier.fillMaxWidth()){Text("Send now")}
                    TextButton({mode="home";wifi.stopDiscovery()}){Text("Back")}
                }
                "receive"->{
                    OutlinedTextField(pin,{pin=it.take(8)},label={Text("Transfer PIN — sender must use the same PIN")},modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp)); CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Waiting for another phone to connect…")
                    Spacer(Modifier.height(12.dp)); Text("Ask the sender to select this phone and use PIN $pin")
                    Spacer(Modifier.weight(1f)); TextButton({mode="home"}){Text("Stop")}
                }
            }
            if(progress>0f&&progress<1f){Spacer(Modifier.height(8.dp));LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())}
        }
    }
}

private fun formatBytes(n:Long):String{if(n<1024)return "$n B"; val k=n/1024.0;if(k<1024)return String.format(Locale.US,"%.1f KB",k);val m=k/1024;if(m<1024)return String.format(Locale.US,"%.1f MB",m);return String.format(Locale.US,"%.2f GB",m/1024)}
