package com.tap.zoot.runtime.host

import io.github.charlietap.chasm.embedding.codegen.CodegenImport

/** A fixed set of direct guest imports owned by one host subsystem. */
internal interface HostImportGroup {
    val imports: List<CodegenImport>
}
