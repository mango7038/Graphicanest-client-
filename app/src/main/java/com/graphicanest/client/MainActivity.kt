package com.graphicanest.client

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
   val displayPrefs=remember{getSharedPreferences("client_display",MODE_PRIVATE)}
   var darkMode by remember{mutableStateOf(displayPrefs.getBoolean("dark_theme",true))}
   ClientTheme(darkTheme=darkMode) {
    val scope=rememberCoroutineScope()
    var clientId by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
    val session=remember{SecureSession(this@MainActivity)}
    var rememberLogin by remember{mutableStateOf(displayPrefs.getBoolean("remember_login",false))}
    var token by remember{mutableStateOf<String?>(null)}
    var projects by remember{mutableStateOf(emptyList<Project>())}
    var chosen by remember{mutableStateOf<Project?>(null)}
    var tab by remember{mutableStateOf("Overview")}
    var detail by remember{mutableStateOf(JSONObject())}
    var documents by remember{mutableStateOf(emptyList<JSONObject>())}
    var photos by remember{mutableStateOf(emptyList<SitePhoto>())}
    var requests by remember{mutableStateOf(emptyList<JSONObject>())}
    var noticeQueue by remember{mutableStateOf(emptyList<Notice>())}
    var message by remember{mutableStateOf("Sign in with credentials provided by your project administrator.")}
    var busy by remember{mutableStateOf(false)}
    var requestText by remember{mutableStateOf("")}
    var profileOpen by remember{mutableStateOf(false)}
    var homeSection by remember{mutableStateOf("Home")}
    var refreshBusy by remember { mutableStateOf(false) }
    var projectError by remember { mutableStateOf("") }
    val detailTabs=listOf("Overview","Updates","Photos","Files","Visits","Chat","Billing","Team")
    LaunchedEffect(Unit){
     if(!rememberLogin){session.clear();return@LaunchedEffect}
     runCatching{withContext(Dispatchers.IO){
      val t=session.access()
      val ps=projects(t)
      runCatching{api("/rest/v1/rpc/siteos_client_device_heartbeat",t,JSONObject().put("p_push_enabled",false))}
      Triple(t,ps,runCatching{notices(t)}.getOrDefault(emptyList()))
     }}.onSuccess{token=it.first;projects=it.second;noticeQueue=it.third}
       .onFailure{message="Unable to connect. Check your connection and sign in or retry."}
    }
    fun projectLoad(p:Project) {
     val t=token?:return
     chosen=p;tab=when(homeSection){"Files"->"Files";"Updates"->"Updates";else->"Overview"};busy=true;projectError=""
     scope.launch{
      runCatching{withContext(Dispatchers.IO){
       val currentToken=session.access()
       val d=JSONObject(api("/rest/v1/rpc/siteos_client_project_detail",currentToken,JSONObject().put("p_project_id",p.id)))
       val files=runCatching{list("/rest/v1/siteos_documents?select=id,title,document_type,storage_path,status&project_id=eq.${p.id}&visible_to_client=eq.true",currentToken)}.getOrDefault(JSONArray())
       val req=runCatching{list("/rest/v1/siteos_change_requests?select=id,description,status,created_at&project_id=eq.${p.id}&order=created_at.desc",currentToken)}.getOrDefault(JSONArray())
       val site=runCatching { fetchSitePhotos(currentToken,p.id) }.getOrDefault(emptyList())
       Pair(Triple(d,(0 until files.length()).map{files.getJSONObject(it)},(0 until req.length()).map{req.getJSONObject(it)}),site)
      }}.onSuccess {detail=it.first.first;documents=it.first.second;requests=it.first.third;photos=it.second}
       .onFailure{projectError="Refresh failed. Previously loaded content is retained.";message="Unable to load project: ${it.message}"}
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
        api("/rest/v1/siteos_client_notification_reads?on_conflict=notification_id,user_id",session.access(),
         JSONObject().put("notification_id",current.id).put("user_id",
          JSONObject(api("/auth/v1/user",session.access())).str("id")))
       }}}
      }){Text("Mark read")}},
      dismissButton={TextButton(onClick={noticeQueue=noticeQueue.drop(1)}){Text("Later")}})
    }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={
     if(token!=null && chosen==null) NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=8.dp){
      listOf("Home","Projects","Updates","Files","Profile").forEach { item ->
       NavigationBarItem(selected=(if(profileOpen)"Profile" else homeSection)==item,
        onClick={if(item=="Profile")profileOpen=true else {profileOpen=false;homeSection=item}},
        icon={Text(when(item){"Home"->"⌂";"Projects"->"▥";"Updates"->"▤";"Files"->"▱";else->"◉"},style=MaterialTheme.typography.titleLarge)},
        label={Text(item)})
      }
     }
    },topBar={Surface(color=MaterialTheme.colorScheme.background,shadowElevation=0.dp){Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){
     Text(if(chosen==null)"GraphicaNest  CLIENT" else chosen!!.business.uppercase(),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onBackground,fontWeight=FontWeight.Bold)
     Row(horizontalArrangement=Arrangement.spacedBy(0.dp)){
      TextButton(onClick={darkMode=!darkMode;displayPrefs.edit().putBoolean("dark_theme",darkMode).apply()}){Text(if(darkMode)"☀" else "☾")}
      if(token!=null) TextButton(onClick={session.clear();token=null;chosen=null;profileOpen=false;projects=emptyList();noticeQueue=emptyList()}){Text("Sign out")}
     }
    }}}){ pad->
     Column(Modifier.fillMaxSize().padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      if(token==null){
       Spacer(Modifier.height(16.dp))
       BrandHero("GRAPHICANEST CLIENT","Track your projects with clarity.","Turning spaces into better lives.")
       Spacer(Modifier.height(20.dp))
       Text("Secure Client Login",style=MaterialTheme.typography.headlineSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
       Text("Sign in with your permanent Client ID and password.",color=Muted)
       OutlinedTextField(clientId,{clientId=it.uppercase().trim()},label={Text("Permanent Client ID")},modifier=Modifier.fillMaxWidth(),singleLine=true)
       var passwordVisible by remember { mutableStateOf(false) }
       OutlinedTextField(password,{password=it},label={Text("Password")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=if(passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={TextButton(onClick={passwordVisible=!passwordVisible}){Text(if(passwordVisible)"Hide" else "Show")}})
       Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
        Checkbox(checked=rememberLogin,onCheckedChange={rememberLogin=it;displayPrefs.edit().putBoolean("remember_login",it).apply()})
        Text("Keep me signed in on this device",style=MaterialTheme.typography.bodySmall)
       }
       Button(enabled=!busy,onClick={
        busy=true;scope.launch{
         runCatching{withContext(Dispatchers.IO){
          val a=JSONObject(api("/functions/v1/siteos-client-login",body=JSONObject().put("client_id",clientId.trim()).put("password",password)))
          val t=a.getString("access_token")
          session.save(a) // Required for authenticated API calls during this launch; next launch clears unless opted in.
          runCatching{api("/rest/v1/rpc/siteos_client_device_heartbeat",t,JSONObject().put("p_push_enabled",false))}
          Triple(t,projects(t),notices(t))
         }}.onSuccess{token=it.first;projects=it.second;noticeQueue=it.third;message=""}
         .onFailure{message="Sign in unsuccessful. Check credentials or contact your administrator."}
         password="";busy=false
        }
       },modifier=Modifier.fillMaxWidth().height(54.dp),colors=ButtonDefaults.buttonColors(containerColor=Purple,contentColor=Color(0xFF001A26)),shape=RoundedCornerShape(50.dp)){Text("🔒  Login   →",fontWeight=FontWeight.Bold)}
       TextButton(enabled=!busy,onClick={
         message="Forgot your password? Contact your project administrator. They can securely issue a new temporary password for your existing Client ID."
       }){Text("Forgot password?")}
       Text("Account activation and first-time password setup are managed by your administrator.",style=MaterialTheme.typography.bodySmall,color=Muted)
      }else if(profileOpen){
       ClientProfileScreen(session,token!!,onBack={profileOpen=false})
      }else if(chosen==null){
       Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
       Text(if(homeSection=="Projects")"My Projects" else if(homeSection=="Updates")"Updates" else if(homeSection=="Files")"Files" else "Your Space",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold)
       Text("View and track all your projects in one place.",color=Muted)
       if(homeSection=="Home")BrandHero("YOUR SPACE","Your projects.","Track project progress with clarity.")
       ClientDashboardSummary(projects.size,projects.count{it.status.contains("Active",true)||it.status.contains("Progress",true)},projects.maxOfOrNull{it.progress} ?: 0)
       var searchQuery by remember { mutableStateOf("") }
       var statusFilter by remember { mutableStateOf("All") }
       OutlinedTextField(searchQuery,{searchQuery=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("⌕  Search projects, location or type...")},singleLine=true,shape=RoundedCornerShape(18.dp))
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
        listOf("All","Active","Completed","On Hold").forEach { v -> FilterChip(selected=statusFilter==v,onClick={statusFilter=v},label={Text(v)}) }
       }
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        Text("Assigned projects",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        TextButton(enabled=!refreshBusy,onClick={refreshBusy=true;scope.launch{
         runCatching{withContext(Dispatchers.IO){val fresh=session.access();token=fresh;Pair(projects(fresh),runCatching{notices(fresh)}.getOrDefault(emptyList()))}}
          .onSuccess{projects=it.first;noticeQueue=it.second;message=""}
          .onFailure{message="Project refresh failed. Try again."}
         refreshBusy=false
        }}){Text(if(refreshBusy)"Refreshing…" else "Refresh")}
       }
       if(projects.isEmpty()) PremiumPanel("No projects shared yet"){Text("Approved projects will appear here after your administrator assigns them.",color=Muted)}
       val shownProjects=projects.filter{(searchQuery.isBlank()||it.name.contains(searchQuery,true)||it.code.contains(searchQuery,true)||it.business.contains(searchQuery,true))&&(statusFilter=="All"||(if(statusFilter=="Active")it.status.contains("active",true)||it.status.contains("progress",true) else if(statusFilter=="Completed")it.status.contains("complete",true) else it.status.contains("hold",true)))}
       Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        shownProjects.forEach{p->
         OutlinedCard(onClick={projectLoad(p)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(23.dp),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF245469))){
          Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
           Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(p.business.uppercase(),color=Purple,style=MaterialTheme.typography.labelMedium);StatusPill(p.status)}
           Text(p.name,style=MaterialTheme.typography.titleMedium,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
           if(p.code.isNotBlank())Text(p.code,color=Muted,style=MaterialTheme.typography.labelSmall)
           ProgressMeter(p.progress)
           Text("View project details  →",color=Purple,style=MaterialTheme.typography.labelLarge)
          }
         }
        }
       }
      }      }else{
       val p=chosen!!
       LazyColumn(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item { TextButton(onClick={chosen=null}){Text("← All projects")} }
        item { BrandHero(p.business,p.name,p.code.ifBlank{"A clear view of your project."}) }
        item { PremiumProjectRing(p.progress) }
        item {
         ScrollableTabRow(selectedTabIndex=detailTabs.indexOf(tab).coerceAtLeast(0),edgePadding=8.dp,containerColor=MaterialTheme.colorScheme.background){
          detailTabs.forEach{v->Tab(selected=tab==v,onClick={tab=v},text={Text(v,maxLines=1)})}
         }
        }
        if(projectError.isNotBlank()) item { Text(projectError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        item{
         AnimatedContent(targetState=tab,transitionSpec={androidx.compose.animation.fadeIn(tween(140)) togetherWith androidx.compose.animation.fadeOut(tween(100))},label="project-tabs"){activeTab->
         when(activeTab){
          "Overview"->{Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
           ProjectQuickActions(onPhotos={tab="Photos"},onBilling={tab="Billing"},onDocuments={tab="Files"})
           Info("Project Overview",fmt(detail,"overview"))
           Info("Current work",fmt(detail,"current_work"))
           Info("Current stage",fmt(detail,"current_stage"))
           Info("Next step",fmt(detail,"next_step"))
           Info("Next step date",fmt(detail,"next_step_date"))
           ProjectMilestoneTimeline(detail)
          }}
          "Progress"->{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
           Info("Current stage",fmt(detail,"current_stage"))
           Info("Ongoing work",fmt(detail,"current_work"))
           Info("Next step",fmt(detail,"next_step"))
           Info("Next milestone date",fmt(detail,"next_step_date"))
          }}
          "Photos"->{SiteGallery(photos,token!!,onRefresh={projectLoad(p)})}
          "Billing"->{Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
           val total=detail.optDouble("contract_value",Double.NaN)
           val paid=detail.optDouble("verified_paid",0.0)
           PremiumPanel("PAYMENT SUMMARY"){
            Text("Remaining contract value",color=Muted,style=MaterialTheme.typography.labelMedium)
            Text(if(total.isNaN())"Not set" else "₹%,.0f".format((total-paid).coerceAtLeast(0.0)),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
             Column {Text("Contract value",color=Muted,style=MaterialTheme.typography.labelSmall);Text(money(detail,"contract_value"))}
             Column {Text("Verified paid",color=Muted,style=MaterialTheme.typography.labelSmall);Text(money(detail,"verified_paid"))}
            }
           }
           PremiumPanel("UPCOMING PAYMENT"){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
             Column {Text("Currently due",color=Muted);Text(money(detail,"amount_due"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
             Column {Text("Next due date",color=Muted);Text(fmt(detail,"next_billing_date"))}
            }
           }
           Text("Only payments verified by your administrator are included in the paid total.",color=Muted,style=MaterialTheme.typography.bodySmall)
          }}
          "Files"->{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
           if(documents.isEmpty()) PremiumPanel("No approved documents yet"){Text("Your project team will publish drawings, receipts and documents here after approval.",color=Muted)}
           Text("${documents.size} approved documents",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
           documents.sortedWith(compareBy<JSONObject>{it.str("document_type")}.thenBy{it.str("title")}).forEach{d->
            OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
             Text(d.str("title"),style=MaterialTheme.typography.titleMedium)
             Text(d.str("document_type").ifBlank{"Project document"},color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)
             Text("Shared with you by the project administrator",style=MaterialTheme.typography.labelSmall,color=Muted)
             Button(enabled=d.str("storage_path").isNotBlank(),onClick={
              val t=token!!
              scope.launch{runCatching{withContext(Dispatchers.IO){savePrivateFile(this@MainActivity,session.access(),d.str("storage_path"),d.str("title"))}}
               .onSuccess{message="Saved to Downloads"}.onFailure{message="Download failed: ${it.message}"}
              }
             }){Text("Download")}
            }}
           }
          }}
          "Chat"->{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
           OutlinedTextField(requestText,{requestText=it},label={Text("Describe the requested change")},modifier=Modifier.fillMaxWidth(),minLines=3)
           Button(enabled=requestText.isNotBlank()&&!busy,onClick={
            busy=true
            scope.launch{
             runCatching{withContext(Dispatchers.IO){
              api("/rest/v1/siteos_change_requests",session.access(),JSONObject().put("project_id",p.id)
               .put("business_id",JSONArray(api("/rest/v1/siteos_projects?select=business_id&id=eq.${p.id}",session.access())).getJSONObject(0).str("business_id"))
               .put("description",requestText).put("status","SUBMITTED"))
             }}.onSuccess{message="Request submitted for review";requestText="";projectLoad(p)}
              .onFailure{message="Request not submitted: ${it.message}"}
             busy=false
            }
           }){Text("Submit change request")}
           requests.forEach{Info(it.str("status"),it.str("description"))}
          }}
          "Updates"->{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
           ProjectMilestoneTimeline(detail)
           requests.forEach{Info(it.str("status"),it.str("description"))}
          }}
          "Visits"->{PremiumPanel("VISITS & MILESTONES"){Text("Site visit details will appear when shared by your project team.");ProjectMilestoneTimeline(detail)}}
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
      }
      if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      if(message.isNotBlank() && (message.contains("failed",true) || message.contains("error",true) || message.contains("submitted",true) || message.contains("recovery",true))) Text(message,style=MaterialTheme.typography.labelSmall)
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
