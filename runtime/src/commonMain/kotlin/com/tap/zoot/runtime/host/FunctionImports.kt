package com.tap.zoot.runtime.host

import io.github.charlietap.chasm.embedding.codegen.FunctionImport
import io.github.charlietap.chasm.embedding.dsl.FunctionTypeBuilder
import io.github.charlietap.chasm.embedding.dsl.ValueTypeListBuilder
import io.github.charlietap.chasm.host.HostFunction

internal fun functionImport(
    module: String,
    name: String,
    parameterTypes: ValueTypeListBuilder.() -> Unit = {},
    resultTypes: ValueTypeListBuilder.() -> Unit = {},
    function: HostFunction,
): FunctionImport =
    FunctionImport(
        moduleName = module,
        entityName = name,
        type =
            FunctionTypeBuilder()
                .apply {
                    params(parameterTypes)
                    results(resultTypes)
                }.build(),
        function = function,
    )

internal fun ValueTypeListBuilder.i32(count: Int) = repeat(count) { i32() }
