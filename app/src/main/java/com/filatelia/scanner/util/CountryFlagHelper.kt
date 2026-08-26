package com.filatelia.scanner.util

import java.util.Locale

object CountryFlagHelper {

    // Mapa exhaustivo de nombres en español, inglés, alemán y denominaciones filatélicas históricas
    private val countryToIsoMap: Map<String, String> = mapOf(
        // América
        "méxico" to "MX", "mexico" to "MX",
        "estados unidos" to "US", "usa" to "US", "united states" to "US", "eeuu" to "US",
        "canadá" to "CA", "canada" to "CA",
        "guatemala" to "GT", "belice" to "BZ", "honduras" to "HN", "el salvador" to "SV",
        "nicaragua" to "NI", "costa rica" to "CR", "panamá" to "PA", "panama" to "PA",
        "cuba" to "CU", "república dominicana" to "DO", "haití" to "HT", "puerto rico" to "PR", "jamaica" to "JM",
        "colombia" to "CO", "venezuela" to "VE", "ecuador" to "EC", "perú" to "PE", "peru" to "PE",
        "bolivia" to "BO", "chile" to "CL", "argentina" to "AR", "uruguay" to "UY", "paraguay" to "PY", "brasil" to "BR", "brazil" to "BR",

        // Europa
        "españa" to "ES", "spain" to "ES",
        "francia" to "FR", "france" to "FR",
        "italia" to "IT", "italy" to "IT", "reino de italia" to "IT",
        "reino unido" to "GB", "gran bretaña" to "GB", "great britain" to "GB", "uk" to "GB", "england" to "GB", "inglaterra" to "GB",
        "portugal" to "PT", "irlanda" to "IE", "bélgica" to "BE", "belgium" to "BE", "belgique" to "BE",
        "países bajos" to "NL", "holanda" to "NL", "netherlands" to "NL",
        "suiza" to "CH", "switzerland" to "CH", "helvetia" to "CH",
        "austria" to "AT", "österreich" to "AT", "imperio austrohúngaro" to "AT",
        "grecia" to "GR", "greece" to "GR", "helas" to "GR",
        "suecia" to "SE", "sweden" to "SE", "noruega" to "NO", "norway" to "NO", "dinamarca" to "DK", "denmark" to "DK",
        "finlandia" to "FI", "finland" to "FI", "islandia" to "IS", "polonia" to "PL", "poland" to "PL", "polska" to "PL",
        "república checa" to "CZ", "checoslovaquia" to "CZ", "eslovaquia" to "SK", "hungría" to "HU", "hungary" to "HU",
        "rumanía" to "RO", "bulgaria" to "BG", "croacia" to "HR", "serbia" to "RS", "yugoslavia" to "RS",
        "eslovenia" to "SI", "bosnia" to "BA", "macedonia" to "MK", "albania" to "AL", "luxemburgo" to "LU",
        "mónaco" to "MC", "andorra" to "AD", "san marino" to "SM", "vaticano" to "VA", "liechtenstein" to "LI", "malta" to "MT", "chipre" to "CY",
        "turquía" to "TR", "turkey" to "TR", "imperio otomano" to "TR",

        // Asia y Oceanía
        "japón" to "JP", "japan" to "JP", "nippon" to "JP",
        "china" to "CN", "taiwan" to "TW", "taiwán" to "TW", "hong kong" to "HK", "macao" to "MO",
        "corea del sur" to "KR", "corea del norte" to "KP", "corea" to "KR",
        "india" to "IN", "pakistán" to "PK", "bangladesh" to "BD", "sri lanka" to "LK", "ceylán" to "LK",
        "singapur" to "SG", "singapore" to "SG",
        "malasia" to "MY", "malaysia" to "MY", "sabah" to "MY", "sarawak" to "MY", "malaya" to "MY",
        "indonesia" to "ID", "filipinas" to "PH", "philippines" to "PH", "tailandia" to "TH", "thailand" to "TH", "siam" to "TH",
        "vietnam" to "VN", "camboya" to "KH", "laos" to "LA", "birmania" to "MM", "myanmar" to "MM",
        "australia" to "AU", "nueva zelanda" to "NZ", "new zealand" to "NZ",
        "israel" to "IL", "arabia saudita" to "SA", "emiratos árabes" to "AE", "qatar" to "QA", "irán" to "IR", "irak" to "IQ",

        // África
        "egipto" to "EG", "egypt" to "EG", "marruecos" to "MA", "morocco" to "MA", "argelia" to "DZ", "túnez" to "TN",
        "sudáfrica" to "ZA", "south africa" to "ZA", "kenia" to "KE", "nigeria" to "NG", "etiopía" to "ET", "senegal" to "SN"
    )

    fun getFlag(countryName: String?): String {
        if (countryName.isNullOrBlank()) return "🌐"
        val clean = countryName.trim().lowercase(Locale.getDefault())

        // 1. Manejo específico para entidades históricas alemanas y filatélicas clave
        if (clean.contains("rda") || clean.contains("ddr") || clean.contains("alemania oriental") || clean.contains("república democrática alemana")) {
            return "🇩🇩"
        }
        if (clean.contains("alemania") || clean.contains("germany") || clean.contains("deutschland") ||
            clean.contains("bundespost") || clean.contains("rfa") || clean.contains("berlín") ||
            clean.contains("berlin") || clean.contains("pfa") || clean.contains("deutsche post") ||
            clean.contains("imperio alemán") || clean.contains("deutsches reich") || clean.contains("reichspost")) {
            return "🇩🇪"
        }
        if (clean.contains("urss") || clean.contains("soviet") || clean.contains("soviet union") || clean.contains("rusia") || clean.contains("russia")) {
            return "🇷🇺"
        }

        // 2. Búsqueda por diccionario filatélico
        for ((nameKey, isoCode) in countryToIsoMap) {
            if (clean.contains(nameKey)) {
                return isoCodeToFlagEmoji(isoCode)
            }
        }

        // 3. Resolución automática por código ISO nativo de Java
        for (iso in Locale.getISOCountries()) {
            val l = Locale("", iso)
            if (clean.contains(l.getDisplayCountry(Locale.forLanguageTag("es")).lowercase()) ||
                clean.contains(l.getDisplayCountry(Locale.ENGLISH).lowercase())) {
                return isoCodeToFlagEmoji(iso)
            }
        }

        return "🌐"
    }

    /**
     * Convierte cualquier código ISO 3166-1 alpha-2 (ej. "MX", "DE", "FR")
     * en su bandera emoji oficial correspondiente mediante Unicode Regional Indicators.
     */
    private fun isoCodeToFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return "🌐"
        val firstChar = Character.codePointAt(countryCode.uppercase(Locale.ROOT), 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(countryCode.uppercase(Locale.ROOT), 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }
}