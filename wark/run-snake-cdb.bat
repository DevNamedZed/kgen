@echo off
cd C:\src\kgen\wark
echo Compiling...
call cmd /c "set JAVA_HOME=C:\Users\ziad\.jdks\azul-24.0.2&& ..\gradlew.bat :wark:compileKotlin :wark:compileTestKotlin --no-daemon -q 2>&1"
echo Running snake JIT under cdb...
echo Will break on access violation and dump registers + stack
echo.
"C:\Program Files (x86)\Windows Kits\10\Debuggers\x64\cdb.exe" -g -G -c "sxe -c \".echo CRASH_REGISTERS;r rax;r rbx;r rcx;r rdx;r rsi;r rdi;r rbp;r rsp;r r8;r r9;r r10;r r11;r r12;r r13;r r14;r r15;r rip;.echo CRASH_INSTRUCTION;u @rip L1;.echo CRASH_AT;r rip;r rbp;r r8;r r9;.echo CRASH_INST;u @rip L1;.echo FUNC_START;u @rip-0x1000 @rip;q\" av;g" C:\Users\ziad\.jdks\azul-24.0.2\bin\java.exe --enable-native-access=ALL-UNNAMED -Xmx1g -cp "build\classes\kotlin\main;build\classes\kotlin\test;build\libs\wark-0.1.0-SNAPSHOT.jar;..\kgen\build\libs\kgen-0.1.0-SNAPSHOT.jar;C:\Users\ziad\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\2.1.10\d3028429e7151d7a7c1a0d63a4f60eac86a87b91\kotlin-stdlib-2.1.10.jar" org.wark.SnakeJitRunnerKt
