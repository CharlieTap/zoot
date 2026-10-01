;; Rebuild: wasm-tools parse unsupported-wasi.wat -o unsupported-wasi.wasm
(module
  (import "wasi_snapshot_preview1" "unsupported_function" (func))
)
