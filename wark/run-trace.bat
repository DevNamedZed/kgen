@echo off
cd C:\src\kgen\wark
C:\Users\ziad\.jdks\azul-24.0.2\bin\java.exe --enable-native-access=ALL-UNNAMED -Xmx1g -cp "build\classes\kotlin\main;build\classes\kotlin\test;..\kgen\build\classes\kotlin\main;..\kgen\build\classes\java\main;C:\Users\ziad\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\2.1.10\d3028429e7151d7a7c1a0d63a4f60eac86a87b91\kotlin-stdlib-2.1.10.jar" org.wark.DoomJitTraceRunnerKt
