package com.graphicanest.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val api = "https://yfzlhhibdflohrjjshei.supabase.co"
private const val key = "sb_publishable_U2h7ptLDzgAWbg2SRs3O4Q_f2mAty2u"

private fun request(path: String, token: String? = null, payload: JSONObject? = null): String {
 val connection = URL(api + path).openConnection() as HttpURLConnection
 try {
  connection.connectTimeout = 15000; connection.readTimeout = 15000
  connection.requestMethod = if(payload == null) "GET" else "POST"
  connection.setRequestProperty("apikey",key)
  connection.setRequestProperty("Content-Type","application/json")
  if(token != null) connection.setRequestProperty("Authorization","Bearer $token")
  if(payload != null) { connection.doOutput = true; connection.outputStream.use { it.write(payload.toString().toByteArray()) } }
  val response = connection.responseCode
  val text = (if(response in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use {it.readText()}.orEmpty()
  if(response !in 200..299) throw IllegalStateException("Request failed ($response). Check login and permissions.")
  return text
 } finally { connection.disconnect() }
}
private data class ClientProject(val id:String,val name:String,val status:String,val progress:Int,val business:String)
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Sign in with your assigned client account.") }
    var busy by remember { mutableStateOf(false) }
    var projects by remember { mutableStateOf(emptyList<ClientProject>()) }
    Scaffold(topBar = { Surface(shadowElevation = 3.dp) { Text("GraphicaNest Client",Modifier.fillMaxWidth().padding(20.dp),style=MaterialTheme.typography.titleLarge) } }) { padding ->
     Column(Modifier.fillMaxSize().padding(padding).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      if(token == null) {
       OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,modifier=Modifier.fillMaxWidth())
       OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
       OutlinedButton(enabled=!busy,onClick={
        busy=true
        scope.launch {
         runCatching { withContext(Dispatchers.IO) {
          require(password.length >= 8)
          request("/auth/v1/signup",payload=JSONObject().put("email",email.trim()).put("password",password))
         } }.onSuccess { status="Verify your email and ask your project admin to assign access." }
           .onFailure { status=it.message ?: "Registration failed" }
         password=""; busy=false
        }
       }) { Text("Create client account") }
       Button(enabled=!busy,onClick={
        busy=true
        scope.launch {
         runCatching { withContext(Dispatchers.IO) {
          val body=JSONObject().put("email",email.trim()).put("password",password)
          val result=JSONObject(request("/auth/v1/token?grant_type=password",payload=body))
          val t=result.getString("access_token")
          val arr=JSONArray(request("/rest/v1/rpc/siteos_client_project_summary",t,JSONObject()))
          Pair(t,(0 until arr.length()).map { i -> val p=arr.getJSONObject(i); ClientProject(p.getString("project_id"),p.getString("project_name"),p.optString("project_status"),p.optInt("progress"),p.getString("business_name")) })
         } }.onSuccess { (t,list) -> token=t; projects=list; status="Signed in. Only assigned projects are displayed." }.onFailure { status=it.message ?: "Login failed" }
         password="";busy=false
        }
       }) {Text("Sign in")}
      } else {
       Row { Text("My projects",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f)); TextButton(onClick={token=null;projects=emptyList();status="Signed out"}) { Text("Sign out") } }
       LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(projects) { p ->
         ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
          Text(p.business,style=MaterialTheme.typography.labelMedium)
          Text(p.name,style=MaterialTheme.typography.titleMedium)
          Text("Status: ${p.status}")
          LinearProgressIndicator(progress={p.progress/100f},modifier=Modifier.fillMaxWidth())
          Text("${p.progress}% complete")
         } }
        }
       }
      }
      Text(status,style=MaterialTheme.typography.bodySmall)
     }
    }
   }
  }
 }
}
