package com.graphicanest.client

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun ClientDashboardSummary(total:Int,active:Int,bestProgress:Int){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
  DashboardMetric("PROJECTS",total.toString(),Modifier.weight(1f))
  DashboardMetric("IN PROGRESS",active.toString(),Modifier.weight(1f))
  DashboardMetric("PROGRESS", "${bestProgress.coerceIn(0,100)}%",Modifier.weight(1f))
 }
}
@Composable
private fun DashboardMetric(label:String,value:String,modifier:Modifier=Modifier){
 Surface(modifier=modifier,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=1.dp){
  Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
   Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
  }
 }
}
@Composable
internal fun ProjectQuickActions(onPhotos:()->Unit,onBilling:()->Unit,onDocuments:()->Unit){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  FilledTonalButton(onClick=onPhotos,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=3.dp)){Text("Photos",maxLines=1)}
  FilledTonalButton(onClick=onBilling,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=3.dp)){Text("Billing",maxLines=1)}
  FilledTonalButton(onClick=onDocuments,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=3.dp)){Text("Files",maxLines=1)}
 }
}
@Composable
internal fun ProjectMilestoneTimeline(detail:JSONObject){
 val stages=listOf("Current stage" to detail.optString("current_stage",""),
  "Current work" to detail.optString("current_work",""),
  "Next milestone" to detail.optString("next_step",""),
  "Expected date" to detail.optString("next_step_date",""))
 PremiumPanel("PROJECT TIMELINE"){
  stages.forEachIndexed{index,item->
   if(item.second.isNotBlank() && item.second!="null"){
    Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)){
     Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(10.dp)){
      Text("${index+1}",modifier=Modifier.padding(horizontal=10.dp,vertical=5.dp),color=MaterialTheme.colorScheme.onPrimaryContainer)
     }
     Column{
      Text(item.first,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
      Text(item.second,style=MaterialTheme.typography.bodyMedium)
     }
    }
   }
  }
 }
}
