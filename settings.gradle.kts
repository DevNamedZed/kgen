rootProject.name = "kgen-root"

include("kgen")

// Code generation (separate — has gson dependency + application plugin)
include("generator")

// CLI (separate — has GraalVM native-image plugin)
include("cli")

// Integration tests
include("integration")

// Example language (JIT + mixed-mode demo)
include("examples:lang")

// API examples (IR builder, assembler, binary, Java-to-native)
include("examples:api")

// GC examples (managed heap, finalization, native types)
include("examples:gc")

// Wark — WASM runtime
include("wark")
include("wark:examples:doom")
include("wark:examples:wasm4")
include("wark:examples:quake3")
