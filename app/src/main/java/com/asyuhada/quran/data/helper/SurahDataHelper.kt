package com.asyuhada.quran.data.helper

data class SurahInfo(
    val arabicName: String,
    val indonesianName: String,
    val verseCount: Int,
    val classification: String, // "Makkiyah" or "Madaniyah"
    val virtue: String // Keutamaan
)

object SurahDataHelper {
    val SURAH_NAMES_AR = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس",
        "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه",
        "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم",
        "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
        "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق",
        "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة",
        "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج",
        "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
        "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد",
        "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات",
        "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
        "المسد", "الإخلاص", "الفلق", "الناس"
    )

    val JUZ_NAMES_AR = listOf(
        "الجزء الأول", "الجزء الثاني", "الجزء الثالث", "الجزء الرابع", "الجزء الخامس",
        "الجزء السادس", "الجزء السابع", "الجزء الثامن", "الجزء التاسع", "الجزء العاشر",
        "الجزء الحادي عشر", "الجزء الثاني عشر", "الجزء الثالث عشر", "الجزء الرابع عشر", "الجزء الخامس عشر",
        "الجزء السادس عشر", "الجزء السابع عشر", "الجزء الثامن عشر", "الجزء التاسع عشر", "الجزء العشرون",
        "الجزء الحادي والعشرون", "الجزء الثاني والعشرون", "الجزء الثالث والعشرون", "الجزء الرابع والعشرون", "الجزء الخامس والعشرون",
        "الجزء السادس والعشرون", "الجزء السابع والعشرون", "الجزء الثامن والعشرون", "الجزء التاسع والعشرون", "الجزء الثلاثون"
    )

    val JUZ_START_PAGES = listOf(
        1, 22, 42, 62, 82, 102, 121, 142, 162, 182, 201, 222, 242, 262, 282, 302, 322, 342, 362, 382, 402, 422, 442, 462, 482, 502, 522, 542, 562, 582
    )

    private val SURAH_VERSES = listOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
    )

    private val MADANIYAH_SURAH_NUMBERS = setOf(
        2, 3, 4, 5, 8, 9, 13, 22, 24, 33, 47, 48, 49, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 76, 98, 99, 110
    )

    fun getSurahInfo(surahNum: Int, indonesianName: String): SurahInfo {
        val arName = SURAH_NAMES_AR.getOrNull(surahNum - 1) ?: ""
        val count = SURAH_VERSES.getOrNull(surahNum - 1) ?: 0
        val isMadaniyah = MADANIYAH_SURAH_NUMBERS.contains(surahNum)
        val classification = if (isMadaniyah) "Madaniyah" else "Makkiyah"

        val virtue = when (surahNum) {
            1 -> "Surat teragung dalam Al-Qur'an, penawar/syifa, dan rukun utama dalam shalat (As-Sab'ul Matsani)."
            2 -> "Melindungi rumah dari gangguan setan, mengandung Ayat Kursi (ayat teragung), serta dua ayat terakhir yang mencukupi pembacanya."
            3 -> "Menaungi pembacanya di hari kiamat bagai awan pelindung bersama surat Al-Baqarah."
            18 -> "Memberikan pancaran cahaya di antara dua Jum'at dan melindungi pembacanya dari fitnah Dajjal jika dihafal 10 ayat pertamanya."
            36 -> "Dikenal sebagai kalbu Al-Qur'an (Jantung Qur'an), mendatangkan kemudahan bagi yang membacanya dengan ikhlas."
            55 -> "Menjelaskan nikmat-nikmat Allah yang melimpah dan mengingatkan jin serta manusia untuk bersyukur."
            56 -> "Membawa kelapangan rezeki dan dianjurkan dibaca setiap malam untuk menghindarkan diri dari kefakiran."
            67 -> "Surat penyelamat dan pembela dari siksa kubur bagi pembaca setianya hingga ia diampuni."
            112 -> "Surat pemurnian tauhid yang keutamaannya setara dengan membaca sepertiga Al-Qur'an."
            113, 114 -> "Dua surat perlindungan (Al-Mu'awwidhatain) terbaik untuk menangkal sihir, hasad, bisikan setan, dan kejahatan makhluk."
            else -> "Surat pembawa berkah, rahmat, syafaat, dan petunjuk hidup bagi setiap orang yang membaca dan merenungkannya."
        }

        return SurahInfo(
            arabicName = arName,
            indonesianName = indonesianName,
            verseCount = count,
            classification = classification,
            virtue = virtue
        )
    }
}
