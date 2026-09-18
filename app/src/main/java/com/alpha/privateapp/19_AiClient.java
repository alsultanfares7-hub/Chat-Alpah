package com.alpha.privateapp;

import android.os.Handler;
import android.os.Looper;

import java.util.function.Consumer;

/*
 * نقطة ربط الذكاء الاصطناعي.
 *
 * الوضع الحالي Demo حتى يشتغل التطبيق بدون API key.
 * عند تجهيز Backend آمن، استبدل ask() بطلب HTTPS إلى خادمك.
 *
 * لا تضع مفتاح API سري داخل APK أو GitHub.
 */
public class AiClient {

    private final Handler handler = new Handler(Looper.getMainLooper());

    public void ask(String prompt, Consumer<String> callback) {
        handler.postDelayed(() -> {
            String answer =
                    "وصلتني فكرتك:\n\n" +
                    "«" + prompt + "»\n\n" +
                    "أنا ALPHA في وضع التجربة حاليًا. " +
                    "الواجهة جاهزة، ونقدر الآن نوصلها بمحرك AI حقيقي عبر Backend آمن.";

            callback.accept(answer);
        }, 550);
    }
}
