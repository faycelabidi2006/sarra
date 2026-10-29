package com.example.familytv.data

import java.security.SecureRandom
import java.util.Calendar

/**
 * ترخيص محلي يعمل بدون أي اتصال (لا يحتاج Supabase).
 *
 * الكود أرقام فقط، والمدة تُستنتج من الكود نفسه:
 *  - الرقم الأخير فردي  -> شهر واحد
 *  - الرقم الأخير زوجي  -> شهران
 *  - الكود FOREVER_CODE -> بلا انتهاء
 */
object License {
    const val FOREVER_CODE = "10121974"

    /** true = جهاز لم يُفعَّل بكود يُغلق أيضاً. false = الأجهزة بلا كود تعمل بلا قيد (السلوك القديم). */
    const val REQUIRE_ACTIVATION = false

    /** قيمة مخزّنة تعني "بلا انتهاء". */
    const val NEVER = Long.MAX_VALUE

    private val rnd = SecureRandom()

    /** عدد الأشهر: 0 = دائم، 1 أو 2 = المدة، null = كود غير صالح. */
    fun monthsFor(raw: String): Int? {
        val code = raw.trim()
        if (code == FOREVER_CODE) return 0
        if (code.length !in 6..12 || !code.all { it in '0'..'9' }) return null
        return if ((code.last() - '0') % 2 == 1) 1 else 2
    }

    /** يولّد كوداً من 8 أرقام برقم أخير يطابق المدة المطلوبة. months: 0 دائم، 1 شهر، 2 شهران. */
    fun generate(months: Int): String {
        if (months == 0) return FOREVER_CODE
        var code: String
        do {
            val head = (1..7).joinToString("") { rnd.nextInt(10).toString() }
            val last = if (months == 1) intArrayOf(1, 3, 5, 7, 9)[rnd.nextInt(5)] else intArrayOf(0, 2, 4, 6, 8)[rnd.nextInt(5)]
            code = head + last
        } while (code == FOREVER_CODE)
        return code
    }

    /** يضيف أشهراً تقويمية فعلية (لا 30 يوماً ثابتة). */
    fun addMonths(from: Long, months: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = from
        c.add(Calendar.MONTH, months)
        return c.timeInMillis
    }
}
