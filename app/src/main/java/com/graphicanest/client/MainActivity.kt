package com.graphicanest.client

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val API="https://yfzlhhibdflohrjjshei.supabase.co"
private const val PUBLIC_KEY="sb_publishable_U2h7ptLDzgAWbg2SRs3O4Q_f2mAty2u"
private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
internal fun api(path:String,token:String?=null,body:JSONObject?=null,method:String=if(body!=null)"POST" else "GET"):String {
 val c=URL(API+path).openConnection() as HttpURLConnection
 try {
  c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=20000
  c.setRequestProperty("apikey",PUBLIC_KEY)
  c.setRequestProperty("Accept","application/json")
  c.setRequestProperty("Content-Type","application/json")
  token?.let{c.setRequestProperty("Authorization","Bearer $it")}
  if(body!=null){c.doOutput=true;c.outputStream.use{it.write(body.toString().toByteArray())}}
  val status=c.responseCode
  val data=(if(status in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
  if(status !in 200..299) throw IllegalStateException("Server error $status")
  return data
 }finally{c.disconnect()}
}
private fun list(path:String,t:String)=JSONArray(api(path,t))
private fun JSONObject.str(k:String)=optString(k,"").takeUnless{it=="null"}.orEmpty()
private data class Project(val id:String,val name:String,val code:String,val status:String,val progress:Int,val business:String)
private data class Notice(val id:String,val title:String,val message:String,val kind:String)
private fun projects(t:String):List<Project>{
 val a=list("/rest/v1/rpc/siteos_client_project_summary",t)
 return (0 until a.length()).map { val v=a.getJSONObject(it)
  Project(v.str("project_id"),v.str("project_name"),v.str("project_code"),v.str("project_status"),v.optInt("progress"),v.str("business_name"))
 }
}
private fun notices(t:String):List<Notice> {
 val a=list("/rest/v1/siteos_client_notifications?select=id,title,message,kind&show_popup=eq.true&order=created_at.desc&limit=15",t)
 val read=list("/rest/v1/siteos_client_notification_reads?select=notification_id",t)
 val readIds=(0 until read.length()).map{read.getJSONObject(it).str("notification_id")}.toSet()
 return (0 until a.length()).map{a.getJSONObject(it)}.filter{it.str("id") !in readIds}.map{Notice(it.str("id"),it.str("title"),it.str("message"),it.str("kind"))}
}
private fun fmt(v:JSONObject,key:String):String=if(v.isNull(key))"Not set" else v.str(key).ifBlank{"Not set"}
private fun savePrivateFile(activity:ComponentActivity,token:String,remote:String,title:String){
 val url="$API/storage/v1/object/authenticated/siteos-private/"+remote.split("/").joinToString("/"){enc(it)}
 val c=URL(url).openConnection() as HttpURLConnection
 try {
  c.setRequestProperty("apikey",PUBLIC_KEY);c.setRequestProperty("Authorization","Bearer $token")
  c.connectTimeout=15000;c.readTimeout=30000
  if(c.responseCode !in 200..299) error("Download not permitted")
  val pdf=remote.lowercase().endsWith(".pdf")
  val mime=if(pdf)"application/pdf" else "image/jpeg"
  val name=(title.replace(Regex("[^a-zA-Z0-9 _.-]"),"_").take(50))+(if(pdf)".pdf" else ".jpg")
  val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,mime);put(MediaStore.Downloads.IS_PENDING,1)}
  val uri=activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:error("Cannot create download")
  try {
   activity.contentResolver.openOutputStream(uri)?.use{out->c.inputStream.use{it.copyTo(out)}}?:error("Cannot write file")
   activity.contentResolver.update(uri,ContentValues().apply{put(MediaStore.Downloads.IS_PENDING,0)},null,null)
  }catch(e:Exception){activity.contentResolver.delete(uri,null,null);throw e}
 }finally{c.disconnect()}
}
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent {
   ClientTheme {
    val scope=rememberCoroutineScope()
    var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
    var token by remember{mutableStateOf<String?>(null)}
    var projects by remember{mutableStateOf(emptyList<Project>())}
    var chosen by remember{mutableStateOf<Project?>(null)}
    var tab by remember{mutableStateOf("Overview")}
    var detail by remember{mutableStateOf(JSONObject())}
    var documents by remember{mutableStateOf(emptyList<JSONObject>())}
    var requests by remember{mutableStateOf(emptyList<JSONObject>())}
    var noticeQueue by remember{mutableStateOf(emptyList<Notice>())}
    var message by remember{mutableStateOf("Sign in with credentials provided by your project administrator.")}
    var busy by remember{mutableStateOf(false)}
    var requestText by remember{mutableStateOf("")}
    var profileOpen by remember{mutableStateOf(false)}
    fun projectLoad(p:Project) {
     val t=token?:return
     chosen=p;tab="Overview";busy=true
     scope.launch{
      runCatching{withContext(Dispatchers.IO){
       val d=JSONObject(api("/rest/v1/rpc/siteos_client_project_detail",t,JSONObject().put("p_project_id",p.id)))
       val files=list("/rest/v1/siteos_documents?select=id,title,document_type,storage_path,status&project_id=eq.${p.id}&visible_to_client=eq.true",t)
       val req=list("/rest/v1/siteos_change_requests?select=id,description,status,created_at&project_id=eq.${p.id}&order=created_at.desc",t)
       Triple(d,(0 until files.length()).map{files.getJSONObject(it)},(0 until req.length()).map{req.getJSONObject(it)})
      }}.onSuccess {detail=it.first;documents=it.second;requests=it.third}
       .onFailure{message="Unable to load project: ${it.message}"}
      busy=false
     }
    }
    val current=noticeQueue.firstOrNull()
    if(current!=null && token!=null){
     AlertDialog(onDismissRequest={noticeQueue=noticeQueue.drop(1)},title={Text(current.title)},text={Text(current.message)},
      confirmButton={TextButton(onClick={
       val t=token!!
       noticeQueue=noticeQueue.drop(1)
       scope.launch{runCatching{withContext(Dispatchers.IO){
        api("/rest/v1/siteos_client_notification_reads?on_conflict=notification_id,user_id",t,
         JSONObject().put("notification_id",current.id).put("user_id",
          JSONObject(api("/auth/v1/user",t)).str("id")))
       }}}
      }){Text("Mark read")}},
      dismissButton={TextButton(onClick={noticeQueue=noticeQueue.drop(1)}){Text("Later")}})
    }
    Scaffold(containerColor=CanvasBg,topBar={Surface(color=CanvasBg,shadowElevation=0.dp){Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){
     Text(if(chosen==null)"GRAPHICANEST  /  CLIENT" else chosen!!.business.uppercase(),style=MaterialTheme.typography.titleMedium,color=Ink)
     if(token!=null) TextButton(onClick={token=null;chosen=null;profileOpen=false;projects=emptyList();noticeQueue=emptyList()}){Text("Sign out")}
    }}}){ pad->
     Column(Modifier.fillMaxSize().padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      if(token==null){
       Spacer(Modifier.height(22.dp))
       BrandHero("Private client portal","Your project, simplified.","Track progress, payments and documents in one secure space.")
       Spacer(Modifier.height(20.dp))
       Text("Welcome back",style=MaterialTheme.typography.headlineSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
       Text("Sign in using credentials shared by your project team.",color=Muted)
       OutlinedTextField(email,{email=it},label={Text("Email")},modifier=Modifier.fillMaxWidth(),singleLine=true)
       OutlinedTextField(password,{password=it},label={Text("Password")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=PasswordVisualTransformation())
       Button(enabled=!busy,onClick={
        busy=true;scope.launch{
         runCatching{withContext(Dispatchers.IO){
          val a=JSONObject(api("/auth/v1/token?grant_type=password",body=JSONObject().put("email",email.trim()).put("password",password)))
          val t=a.getString("access_token")
          Triple(t,projects(t),notices(t))
         }}.onSuccess{token=it.first;projects=it.second;noticeQueue=it.third;message="Signed in. Only assigned projects are shown."}
         .onFailure{message="Login failed: ${it.message}"}
         password="";busy=false
        }
       },modifier=Modifier.fillMaxWidth()){Text("Sign in")}
       Text("Your account is managed by GraphicaNest. Contact your project manager for access.",style=MaterialTheme.typography.bodySmall,color=Muted)
      }else if(profileOpen){
       ClientProfileScreen(token!!,onBack={profileOpen=false})
      }else if(chosen==null){
       BrandHero("Your workspace","My projects","Everything important about your projects, in one place.")
       TextButton(onClick={profileOpen=true}){Text("My Profile & Settings →")}
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        Text("Assigned projects",style=MaterialTheme.typography.titleLarge)
        TextButton(onClick={val t=token!!;scope.launch{
         runCatching{withContext(Dispatchers.IO){Pair(projects(t),notices(t))}}
          .onSuccess{projects=it.first;noticeQueue=it.second}
        }}){Text("Refresh")}
       }
       LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.weight(1f)){
        items(projects){p->
         ElevatedCard(onClick={projectLoad(p)},modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),colors=CardDefaults.elevatedCardColors(containerColor=androidx.compose.ui.graphics.Color.White)){
          Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
           Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(p.business.uppercase(),color=Purple,style=MaterialTheme.typography.labelMedium);StatusPill(p.status)}
           Text(p.name,style=MaterialTheme.typography.titleMedium,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
           if(p.code.isNotBlank())Text(p.code,color=Muted,style=MaterialTheme.typography.labelSmall)
           ProgressMeter(p.progress)
           Text("View project details  →",color=Purple,style=MaterialTheme.typography.labelLarge)
          }
         }
        }
       }
      }else{
       val p=chosen!!
       TextButton(onClick={chosen=null}){Text("← All projects")}
       BrandHero(p.business,p.name,"Current status: ${p.status}")
       ProgressMeter(p.progress)
       ScrollableTabRow(selectedTabIndex=listOf("Overview","Billing","Documents","Requests","Team").indexOf(tab),edgePadding=0.dp){
        listOf("Overview","Billing","Documents","Requests","Team").forEach{v->Tab(selected=tab==v,onClick={tab=v},text={Text(v)})}
       }
       LazyColumn(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
         when(tab){
          "Overview"->{Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
           Info("Project Overview",fmt(detail,"overview"))
           Info("Current work",fmt(detail,"current_work"))
           Info("Current stage",fmt(detail,"current_stage"))
           Info("Next step",fmt(detail,"next_step"))
           Info("Next step date",fmt(detail,"next_step_date"))
          }}
          "Billing"->{Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
           Info("Total contract value",money(detail,"contract_value"))
           Info("Verified payments received",money(detail,"verified_paid"))
           val total=detail.optDouble("contract_value",Double.NaN)
           val paid=detail.optDouble("verified_paid",0.0)
           Info("Remaining contract value",if(total.isNaN())"Not set" else "₹%.2f".format((total-paid).coerceAtLeast(0.0)))
           Info("Currently due",money(detail,"amount_due"))
           Info("Next billing date",fmt(detail,"next_billing_date"))
           Text("Payment status is based on admin-verified transactions only.",style=MaterialTheme.typography.bodySmall)
          }}
          "Documents"->{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
           if(documents.isEmpty())Text("No documents published for this project.")
           documents.forEach{d->
            OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
             Text(d.str("title"),style=MaterialTheme.typography.titleMedium)
             Text(d.str("document_type"))
             Button(enabled=d.str("storage_path").isNotBlank(),onClick={
              val t=token!!
              scope.launch{runCatching{withContext(Dispatchers.IO){savePrivateFile(this@MainActivity,t,d.str("storage_path"),d.str("title"))}}
               .onSuccess{message="Saved to Downloads"}.onFailure{message="Download failed: ${it.message}"}
              }
             }){Text("Download")}
            }}
           }
          }}
          "Requests"->{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
           OutlinedTextField(requestText,{requestText=it},label={Text("Describe the requested change")},modifier=Modifier.fillMaxWidth(),minLines=3)
           Button(enabled=requestText.isNotBlank()&&!busy,onClick={
            busy=true
            scope.launch{
             runCatching{withContext(Dispatchers.IO){
              api("/rest/v1/siteos_change_requests",token,JSONObject().put("project_id",p.id)
               .put("business_id",JSONArray(api("/rest/v1/siteos_projects?select=business_id&id=eq.${p.id}",token)).getJSONObject(0).str("business_id"))
               .put("description",requestText).put("status","SUBMITTED"))
             }}.onSuccess{message="Request submitted for review";requestText="";projectLoad(p)}
              .onFailure{message="Request not submitted: ${it.message}"}
             busy=false
            }
           }){Text("Submit change request")}
           requests.forEach{Info(it.str("status"),it.str("description"))}
          }}
          "Team"->{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
           Info("Contractor",fmt(detail,"contractor_name"))
           PhoneButton("Call contractor",detail.str("contractor_phone"))
           Info("Designer",fmt(detail,"designer_name"))
           PhoneButton("Call designer",detail.str("designer_phone"))
          }}
         }
        }
       }
      }
      if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      Text(message,style=MaterialTheme.typography.labelSmall)
     }
    }
   }
  }
 }
}
@Composable internal fun Info(label:String,value:String){
 PremiumPanel(label){Text(value,style=MaterialTheme.typography.bodyLarge)}
}
private fun money(o:JSONObject,k:String)=if(o.isNull(k)||!o.has(k))"Not set" else "₹%.2f".format(o.optDouble(k))
@Composable private fun PhoneButton(label:String,number:String){
 val ctx=LocalContext.current
 if(number.isNotBlank())OutlinedButton(onClick={ctx.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$number")))}){Text(label)}
}
