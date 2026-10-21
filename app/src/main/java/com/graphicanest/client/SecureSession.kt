package com.graphicanest.client

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Access/refresh tokens are encrypted with an AndroidKeyStore AES-GCM key. */
internal class SecureSession(context: Context) {
 private val prefs=context.getSharedPreferences("gn_client_session_v1",Context.MODE_PRIVATE)
 private fun secret():SecretKey {
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  val alias="gn_client_aes_gcm_v1"
  (ks.getKey(alias,null) as? SecretKey)?.let{return it}
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
   init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 fun save(tokens:JSONObject){
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,secret())}
  val content=JSONObject().put("access_token",tokens.getString("access_token"))
   .put("refresh_token",tokens.getString("refresh_token"))
   .put("expiry",System.currentTimeMillis()+tokens.optLong("expires_in",3600)*1000)
   .toString().toByteArray()
  prefs.edit().putString("session",Base64.encodeToString(cipher.iv+cipher.doFinal(content),Base64.NO_WRAP)).apply()
 }
 fun load():JSONObject?=runCatching{
  val stored=prefs.getString("session",null)?:return null
  val raw=Base64.decode(stored,Base64.NO_WRAP)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,raw.copyOfRange(0,12)))}
  JSONObject(String(cipher.doFinal(raw.copyOfRange(12,raw.size))))
 }.getOrNull()
 fun clear(){prefs.edit().remove("session").apply()}
 fun access():String{
  val stored=load()?:error("Please sign in")
  if(stored.getLong("expiry")>System.currentTimeMillis()+60000)return stored.getString("access_token")
  val refreshed=JSONObject(api("/auth/v1/token?grant_type=refresh_token",body=JSONObject().put("refresh_token",stored.getString("refresh_token"))))
  save(refreshed)
  return refreshed.getString("access_token")
 }
}
