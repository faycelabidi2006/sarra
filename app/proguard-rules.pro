# ===== قواعد R8 لتطبيق FamilyTv =====
# الهدف: تعتيم أسماء الأصناف والدوال الخاصة بنا لتصعيب قراءة الكود عند فك حزمة الـ APK،
# مع الحفاظ على عمل المكتبات (Media3 / OkHttp / Coil / Compose) التي تأتي بقواعدها الخاصة تلقائياً.

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile
-renamesourcefileattribute SourceFile

# OkHttp وOkio: أصناف اختيارية لمنصّات غير مستخدمة هنا، تحذيراتها غير ضارة
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# لا حاجة لقواعد Keep يدوية لنماذج البيانات في data/Models.kt: تُبنى يدوياً عبر org.json
# (لا تُستخدم مكتبة انعكاس مثل Gson أو kotlinx.serialization)، فتعتيمها آمن تماماً.
