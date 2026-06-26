#!/bin/bash
if [ -f "../mizan1.zip" ]; then
    echo "تم العثور على ملف mizan1.zip جديد، جاري فك الضغط والتحديث..."
    unzip -o ../mizan1.zip -d ./
fi
git add .
git commit -m "تحديث تلقائي"
git push origin main
