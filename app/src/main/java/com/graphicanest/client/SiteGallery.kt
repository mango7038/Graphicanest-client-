package com.graphicanest.client

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class SitePhoto(val id:String,val path:String,val caption:String,val date:String)
private const val photoBase="https://yfzlhhibdflohrjjshei.supabase.co"
private const val photoKey="sb_publishable_U2h7ptLDzgAWbg2SRs3O4Q_f2mAty2u"
internal fun fetchSitePhotos(token:String,projectId:String):List<SitePhoto>{
 val a=JSONArray(api("/rest/v1/siteos_site_photos?select=id,storage_path,caption,captured_at&project_id=eq.$projectId&visible_to_client=eq.true&order=captured_at.desc",token))
 return (0 until a.length()).map{a.getJSONObject(it)}.map{SitePhoto(it.optString("id"),it.optString("storage_path"),it.optString("caption"),it.optString("captured_at").take(10))}
}
@Composable
internal fun SiteGallery(photos:List<SitePhoto>,token:String,onRefresh:()->Unit){
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   Text("Site progress photos",style=MaterialTheme.typography.titleLarge)
   TextButton(onClick=onRefresh){Text("Refresh")}
  }
  Text("Photos shared by your project team.",color=Muted,style=MaterialTheme.typography.bodySmall)
  if(photos.isEmpty()) PremiumPanel("No published photos yet"){
   Text("Once your project team shares site photos, they will appear here.")
  }
  photos.forEach{photo->
   PremiumPanel(photo.date.ifBlank{"Site update"}){
    var bmp by remember(photo.id,token){mutableStateOf<android.graphics.Bitmap?>(null)}
    var failed by remember(photo.id,token){mutableStateOf(false)}
    LaunchedEffect(photo.id,token){
     val result=runCatching{withContext(Dispatchers.IO){
      val path=photo.path.split("/").joinToString("/"){URLEncoder.encode(it,"UTF-8")}
      val c=URL("$photoBase/storage/v1/object/authenticated/siteos-private/$path").openConnection() as HttpURLConnection
      try {
       c.connectTimeout=15000;c.readTimeout=20000
       c.setRequestProperty("apikey",photoKey)
       c.setRequestProperty("Authorization","Bearer $token")
       if(c.responseCode!=200) error("Photo not accessible")
       c.inputStream.use{BitmapFactory.decodeStream(it)}
      }finally{c.disconnect()}
     }}
     result.onSuccess{bmp=it}.onFailure{failed=true}
    }
    if(bmp!=null)Image(bitmap=bmp!!.asImageBitmap(),contentDescription=photo.caption.ifBlank{"Site progress photograph"},modifier=Modifier.fillMaxWidth().height(210.dp),contentScale=ContentScale.Crop)
    else if(failed)Text("Image unavailable")
    else LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
    if(photo.caption.isNotBlank())Text(photo.caption,style=MaterialTheme.typography.bodyMedium)
   }
  }
 }
}
