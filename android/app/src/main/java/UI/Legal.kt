package com.example.myapplication.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Las páginas legales viven en la web (así se actualizan sin sacar versión nueva de la app). */
const val URL_LEGAL = "https://micarrera-beta.vercel.app/"

/** Abre "privacidad" o "terminos" en el navegador. */
fun abrirPagina(context: Context, pagina: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$URL_LEGAL$pagina.html"))) }
}
