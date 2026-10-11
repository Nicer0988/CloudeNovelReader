package com.example.novelreader

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.novelreader.R.string.default_web_client_id
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

object Cloud {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun signIn(act: Activity): String? {
        val opt = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(act.getString(default_web_client_id))
            .build()
        val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
        val cred = CredentialManager.create(act).getCredential(act, req).credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await()
        }
        return auth.currentUser?.email
    }

    private suspend fun clearChapters(col: CollectionReference) {
        col.get().await().documents.chunked(400).forEach { b ->
            val wb = db.batch()
            b.forEach { wb.delete(it.reference) }
            wb.commit().await()
        }
    }

    suspend fun deleteNovel(id: String) {
        val u = auth.currentUser ?: return
        val d = db.collection("users").document(u.uid).collection("novels").document(id)
        clearChapters(d.collection("chapters"))
        d.delete().await()
    }

    // pullSettings = true сразу после входа: забираем тему, язык, вкладки из облака
    suspend fun sync(st: Store, pullSettings: Boolean) {
        val u = auth.currentUser ?: return
        st.syncing = true
        try {
            withContext(Dispatchers.IO) {
                val root = db.collection("users").document(u.uid)

                // --- настройки ---
                val sd = root.get().await()
                if (pullSettings && sd.exists()) {
                    (sd.get("tabs") as? List<*>)?.map { it.toString() }?.forEach { if (it !in st.tabs) st.tabs.add(it) }
                    sd.getLong("theme")?.let { st.theme = it.toInt() }
                    sd.getLong("lang")?.let { st.lang = it.toInt() }
                    (sd.get("cfg") as? Map<*, *>)?.let { m ->
                        st.cfg = ReaderCfg(
                            (m["font"] as? Number)?.toInt() ?: 0,
                            (m["size"] as? Number)?.toFloat() ?: 18f,
                            (m["line"] as? Number)?.toFloat() ?: 1.4f,
                            (m["margin"] as? Number)?.toFloat() ?: 16f,
                            (m["spacing"] as? Number)?.toFloat() ?: 0f
                        )
                    }
                }

                // --- скачать из облака то, чего нет у нас или что новее ---
                val remote = root.collection("novels").get().await()
                for (d in remote.documents) {
                    val id = d.id
                    val rrev = d.getLong("rev") ?: 0L
                    val loc = st.novels.firstOrNull { it.id == id }
                    if (loc == null || rrev > loc.rev) {
                        val chs = root.collection("novels").document(id).collection("chapters")
                            .orderBy("i").get().await().documents
                            .map { Chapter(it.getString("t") ?: "", it.getString("x") ?: "") }
                        st.saveChapters(id, chs)
                        var cover = loc?.cover
                        d.getString("cover")?.let { b64 ->
                            val f = File(st.dir, "cover_$id.jpg")
                            f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                            cover = f.path
                        }
                        val n = Novel(id, d.getString("title") ?: "", d.getString("tab") ?: "@planned",
                            cover, d.getLong("color") ?: 0xFF6C5CE7, rrev)
                        if (loc == null) st.novels.add(n) else st.novels[st.novels.indexOf(loc)] = n
                    }
                }

                // --- загрузить в облако наше ---
                val remoteRev = remote.documents.associate { it.id to (it.getLong("rev") ?: 0L) }
                for ((id, title, tab, cover, color, rev) in st.novels.toList()) {
                    val rr = remoteRev[id]
                    val doc = root.collection("novels").document(id)
                    var cv: String? = null
                    if (rr == null || rev > rr) {
                        val col = doc.collection("chapters")
                        clearChapters(col)
                        // картинки манги не загружаем, длинные главы режем на куски
                        val pieces = st.chapters(id)
                            .filter { !it.text.startsWith("IMG\n") }
                            .flatMap { c ->
                                c.text.chunked(300000).ifEmpty { listOf("") }
                                    .mapIndexed { k, x -> Chapter(if (k == 0) c.title else "${c.title} (${k + 1})", x) }
                            }
                        pieces.withIndex().chunked(400).forEach { b ->
                            val wb = db.batch()
                            b.forEach { (i, c) ->
                                wb.set(col.document("%06d".format(i)), mapOf("i" to i, "t" to c.title, "x" to c.text))
                            }
                            wb.commit().await()
                        }
                        cv = cover?.let { File(it) }?.takeIf { it.exists() && it.length() < 600_000 }
                            ?.let { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
                    }
                    val meta = mutableMapOf<String, Any>("title" to title, "tab" to tab, "color" to color, "rev" to rev)
                    if (cv != null) meta["cover"] = cv
                    doc.set(meta, SetOptions.merge()).await()
                }

                // --- наши настройки в облако ---
                val c = st.cfg
                root.set(
                    mapOf(
                        "tabs" to st.tabs.toList(), "theme" to st.theme, "lang" to st.lang,
                        "cfg" to mapOf("font" to c.font, "size" to c.size.toDouble(), "line" to c.line.toDouble(),
                            "margin" to c.margin.toDouble(), "spacing" to c.spacing.toDouble())
                    ), SetOptions.merge()
                ).await()
            }
            st.save()
        } finally {
            st.syncing = false
        }
    }
}