package com.filatelia.scanner.catalog

import java.net.URLEncoder

/**
 * Los catálogos Scott, Michel y Yvert son bases de datos privadas y de pago;
 * no ofrecen una API pública para consultar por imagen o por texto. Por eso,
 * en vez de simular datos que no podemos verificar, generamos enlaces de
 * búsqueda directos para que el usuario confirme el número de catálogo con
 * la fuente oficial. También incluimos Colnect y StampWorld, que sí tienen
 * catálogos comunitarios navegables públicamente.
 */
object CatalogLinkBuilder {

    data class CatalogLink(val label: String, val url: String)

    fun buildLinks(country: String?, series: String?, faceValue: String?, catalogNumber: String? = null): List<CatalogLink> {
        val queryParts = listOfNotNull(country, series, faceValue, catalogNumber, "sello", "stamp")
        val query = URLEncoder.encode(queryParts.joinToString(" "), "UTF-8")

        return listOf(
            CatalogLink("Buscar en Colnect", "https://colnect.com/es/stamps/list/search/$query"),
            CatalogLink("Buscar en StampWorld", "https://www.stampworld.com/es/search/?q=$query"),
            CatalogLink("Buscar catálogo Scott (Google)", "https://www.google.com/search?q=Scott+catalog+$query"),
            CatalogLink("Buscar catálogo Michel (Google)", "https://www.google.com/search?q=Michel+catalog+$query"),
            CatalogLink("Buscar catálogo Yvert (Google)", "https://www.google.com/search?q=Yvert+et+Tellier+$query")
        )
    }
}
