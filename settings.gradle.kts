rootProject.name = "kgen"

// Core
include("ir")
include("pass")
include("object")
include("linker")
include("tools")

// Code generation
include("generator")

// Backends
include("backend-jvm")
include("backend-arm64")
include("backend-x86_64")
include("backend-wasm")
include("backend-riscv")
include("backend-msil")

// Integration tests
include("integration")
