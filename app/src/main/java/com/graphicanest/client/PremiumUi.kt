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

val Ink = Color(0xFFE9F7FF)
val Muted = Color(0xFFA5B8CE)
val Purple = Color(0xFF00DDE9)
val Cyan = Color(0xFF168DFF)
val CanvasBg = Color(0xFF051321)

@Composable
fun ClientTheme(darkTheme:Boolean=false,content: @Composable () -> Unit) {
 val scheme = if(darkTheme) darkColorScheme(
  primary=Purple,onPrimary=Color(0xFF001A26),secondary=Cyan,
  background=CanvasBg,surface=Color(0xFF102838),onSurface=Ink,onBackground=Ink,
  surfaceVariant=Color(0xFF17394B),onSurfaceVariant=Muted) else lightColorScheme(primary=Purple,onPrimary=Color.White,secondary=Cyan,
  background=CanvasBg,surface=Color.White,onSurface=Ink,onBackground=Ink,
  surfaceVariant=Color(0xFFF0EDE8),onSurfaceVariant=Muted)
 MaterialTheme(colorScheme=scheme,content=content)
}
@Composable
fun BrandHero(eyebrow:String,title:String,description:String,modifier:Modifier=Modifier){
 Column(modifier.fillMaxWidth().background(
   Brush.linearGradient(listOf(Color(0xFF10293B),Color(0xFF0B3446),Color(0xFF065066))),
   RoundedCornerShape(25.dp)).padding(horizontal=23.dp,vertical=27.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  Text(eyebrow.uppercase(),color=Purple,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
  Text(title,color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
  Text(description,color=Color(0xFFE0E9E7),style=MaterialTheme.typography.bodyMedium)
 }
}
@Composable
fun PremiumPanel(label:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
 OutlinedCard(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Color(0xFF1B5264)),
  colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),
  ){
  Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
   content()
  }
 }
}
@Composable
fun StatusPill(text:String){
 Surface(color=Color(0xFF073F3D),shape=RoundedCornerShape(100.dp)){
  Text(text,color=Color(0xFF3AF3CC),modifier=Modifier.padding(horizontal=11.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall)
 }
}
@Composable
fun ProgressMeter(progress:Int){
 Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
  Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
   Text("Project progress",modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)
   Text("$progress%",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
  }
  LinearProgressIndicator(progress={progress.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth().height(8.dp),
   color=MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.surfaceVariant,gapSize=0.dp,drawStopIndicator={})
 }
}
