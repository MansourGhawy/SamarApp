#!/bin/bash

# 1. التحقق من وجود ملف mizan1.zip جديد وفك ضغطه تلقائياً
if [ -f "../mizan1.zip" ]; then
    echo "تم العثور على ملف mizan1.zip جديد، جاري فك الضغط والتحديث..."
    unzip -o ../mizan1.zip -d ./
    
    # (اختياري) يمكنك إزالة علامة # من السطر التالي لحذف ملف الـ zip بعد فكه تلقائياً لتوفير المساحة:
    # rm ../mizan1.zip
else
    echo "لم يتم العثور على ملف mizan1.zip جديد، سيتم رفع التعديلات الحالية فقط."
fi

# 2. رفع الملفات إلى GitHub
git add .
git commit -m "تحديث تلقائي"
git push origin main
