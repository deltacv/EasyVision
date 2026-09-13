package org.deltacv.visiongraph.annotation.polymorphicserializable

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class PolymorphicSerializableAnnotationProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return PolymorphicSerializableAnnotationProcessor(
            environment
        )
    }
}