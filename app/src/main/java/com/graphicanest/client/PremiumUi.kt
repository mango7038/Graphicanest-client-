package com.graphicanest.client

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF13213A)
val Muted = Color(0xFF8490A3)
val Purple = Color(0xFF194E66)
val Cyan = Color(0xFFB7936A)
val CanvasBg = Color(0xFFF8F7F4)

@Composable
fun ClientTheme(content: @Composable () -> Unit) {
 val scheme = lightColorScheme(primary=Purple,onPrimary=Color.White,secondary=Cyan,
  background=CanvasBg,surface=Color.White,onSurface=Ink,onBackground=Ink,
  surfaceVariant=Color(0xFFF0EDE8),onSurfaceVariant=Muted)
 MaterialTheme(colorScheme=scheme,content=content)
}
@Composable
fun BrandHero(eyebrow:String,title:String,description:String,modifier:Modifier=Modifier){
 Column(modifier.fillMaxWidth().background(
   Brush.linearGradient(listOf(Color(0xFF13263A),Color(0xFF1F4956),Color(0xFF3D6564))),
   RoundedCornerShape(18.dp)).padding(horizontal=25.dp,vertical=34.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  Text(eyebrow.uppercase(),color=Color(0xFFE6C9A3),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
  Text(title,color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
  Text(description,color=Color(0xFFE0E9E7),style=MaterialTheme.typography.bodyMedium)
 }
}
@Composable
fun PremiumPanel(label:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
 ElevatedCard(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
  colors=CardDefaults.elevatedCardColors(containerColor=Color.White),
  elevation=CardDefaults.elevatedCardElevation(defaultElevation=0.dp)){
  Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(label,color=Muted,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
   content()
  }
 }
}
@Composable
fun StatusPill(text:String){
 Surface(color=Color(0xFFEAF4EF),shape=RoundedCornerShape(100.dp)){
  Text(text,color=Color(0xFF246F56),modifier=Modifier.padding(horizontal=11.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall)
 }
}
@Composable
fun ProgressMeter(progress:Int){
 Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
  Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
   Text("Project progress",modifier=Modifier.weight(1f),color=Muted,style=MaterialTheme.typography.labelMedium)
   Text("$progress%",color=Ink,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
  }
  LinearProgressIndicator(progress={progress.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth().height(8.dp),
   color=Purple,trackColor=Color(0xFFE6E9E8),gapSize=0.dp,drawStopIndicator={})
 }
}
