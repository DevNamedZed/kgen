rootProject.name = "kgen"

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
