package org.deltacv.visiongraph.annotation.codectype

import com.google.devtools.ksp.processing.*

class CodecTypeAnnotationProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        CodecTypeAnnotationProcessor(environment)
}



