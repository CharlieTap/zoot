package com.tap.zoot.runtime.host

import at.released.weh.host.EmbedderHost
import com.tap.zoot.runtime.generated.ootWasmModule
import io.github.charlietap.chasm.embedding.codegen.FunctionImport
import io.github.charlietap.chasm.embedding.dropStore
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.runtime.type.ExternalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class GuestImportContractTest {
    @Test
    fun registeredHostFunctionsMatchTheShippingGuest() {
        val guest = module(binary()).expect("Decode shipping guest")
        val imports = hostTestImports().filterIsInstance<FunctionImport>()
        val provided = imports.associateBy { it.moduleName to it.entityName }
        assertEquals(imports.size, provided.size)

        val required = guest.imports.filterNot { it.moduleName == "wasi_snapshot_preview1" }
        for (definition in required) {
            val key = definition.moduleName to definition.entityName
            val actual = provided[key]
            assertNotNull("Missing host import $key", actual)
            assertEquals("Signature for $key", (definition.type as ExternalType.Function).functionType, actual!!.type)
        }
        assertEquals(
            setOf("oot_profile" to "fast3d"),
            provided.keys - required.map { it.moduleName to it.entityName }.toSet(),
        )
    }

    @Test
    fun shippingGuestInstantiatesWithAutomaticWasiAndTheUnusedProfilerImport() {
        var store: Store? = null
        EmbedderHost().use { host ->
            try {
                val guest =
                    ootWasmModule(
                        binary = binary(),
                        wasiHost = host,
                        imports = hostTestImports(),
                        instanceFactory = { owner, module, imports ->
                            store = owner
                            instance(owner, module, imports).expect("Instantiate shipping guest")
                        },
                    )
                assertEquals(3, guest.ootAbiVersion())
            } finally {
                store?.let(::dropStore)
            }
        }
    }

    private fun binary() = File(requireNotNull(System.getProperty("guestBinary"))).readBytes()
}
