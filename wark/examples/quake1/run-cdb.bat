@echo off
cd /d "C:\src\kgen\wark\examples\quake1\build\bin"
"C:\Program Files (x86)\Windows Kits\10\Debuggers\x64\cdb.exe" -g -c "sxd av;g;r;u @rip-0x10 L20;kn 10;q" "C:\Users\ziad\.jdks\azul-24.0.2\bin\java.exe" --enable-native-access=ALL-UNNAMED -Xmx512m -jar wark-quake1-0.1.0-SNAPSHOT.jar quake.wasm . jit
