package com.graphicanest.client

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
internal fun ClientProfileScreen(session:SecureSession, token:String, onBack:()->Unit) {
 val scope=rememberCoroutineScope()
 var profile by remember { mutableStateOf(JSONObject()) }
 var freshPassword by remember { mutableStateOf("") }
 var info by remember { mutableStateOf("") }
 var busy by remember { mutableStateOf(false) }
 LaunchedEffect(token) {
  runCatching { withContext(Dispatchers.IO) { JSONObject(api("/rest/v1/rpc/siteos_my_client_profile",session.access(),JSONObject())) } }
   .onSuccess { profile=it }.onFailure { info="Unable to load profile: ${it.message}" }
 }
 LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item {
   TextButton(onClick=onBack){Text("← My projects")}
   BrandHero("GRAPHICANEST CLIENT","Profile","Manage your account, preferences and app settings.")
   Spacer(Modifier.height(16.dp))
   Text("Account & Security",style=MaterialTheme.typography.titleLarge)
   val clients=profile.optJSONArray("client_records")?:JSONArray()
   if(clients.length()==0) Info("Client details","Client record has not yet been linked by your project administrator.")
   (0 until clients.length()).forEach{i->
    val c=clients.getJSONObject(i)
    PremiumPanel(c.optString("business","Client").uppercase()+"  •  ACCOUNT"){
     Text(c.optString("name",""),style=MaterialTheme.typography.titleMedium)
     Text("Client ID: "+c.optString("client_id","Not set"))
     Text("Email: "+c.optString("email","Not set"))
     Text("Mobile: "+c.optString("phone","Not set"))
     Text("Alternate: "+c.optString("alternate_phone","Not set"))
     Text("Address: "+listOf("address","city","state","postal_code").map{c.optString(it,"")}.filter{it.isNotBlank()}.joinToString(", "))
    }
   }
   val ps=profile.optJSONArray("projects")?:JSONArray()
   PremiumPanel("LINKED PROJECTS") {
    (0 until ps.length()).forEach{i->
     val p=ps.getJSONObject(i)
     Text(p.optString("name")+" • "+p.optString("business"))
    }
   }
   Spacer(Modifier.height(8.dp))
   Text("Account Security",style=MaterialTheme.typography.titleMedium)
   OutlinedTextField(freshPassword,{freshPassword=it},label={Text("New password (minimum 6 characters)")},singleLine=true,
    visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
   Button(enabled=freshPassword.length>=6 && !busy,onClick={
    busy=true
    scope.launch {
     runCatching { withContext(Dispatchers.IO) { api("/auth/v1/user",session.access(),JSONObject().put("password",freshPassword),method="PUT") } }
      .onSuccess { info="Password updated";freshPassword="" }
      .onFailure { info="Password update failed: ${it.message}" }
     busy=false
    }
   }){Text("Update password")}
   PremiumPanel("NOTIFICATIONS"){Text("Project updates are shown when shared by your team.")}
   PremiumPanel("HELP & SUPPORT"){Text("Contact your project administrator for access and account assistance.")}
   Text("For corrections to contact details, ask your project manager.",style=MaterialTheme.typography.bodySmall,color=Muted)
   if(info.isNotBlank())Text(info,style=MaterialTheme.typography.bodySmall)
  }
 }
}
