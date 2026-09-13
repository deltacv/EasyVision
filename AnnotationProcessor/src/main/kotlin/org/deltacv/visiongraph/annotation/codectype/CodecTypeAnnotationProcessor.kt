package org.deltacv.visiongraph.annotation.codectype

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*

class CodecTypeAnnotationProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val logger = environment.logger

    private val moduleName = environment.options["moduleName"] ?: ""
    private val packageName: String = environment.options["codecTypeClassesMetadataPackage"] ?: "org.deltacv.visiongraph.serialization.v2.generated"
    private val fileName: String = moduleName + (environment.options["codecTypeClassesMetadataClassName"] ?: "CodecTypeMetadata")

    private val codecTypeRegistryClass = ClassName(
        "org.deltacv.visiongraph.serialization.v2",
        "CodecTypeRegistry"
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
            .getSymbolsWithAnnotation("org.deltacv.visiongraph.serialization.v2.CodecType")
            .filterIsInstance<KSClassDeclaration>()

        val deferred = symbols.filterNot { it.validate() }.toList()
        val valid = symbols.filter { it.validate() }

        data class Entry(val typeName: String, val cls: KSClassDeclaration, val instantiable: Boolean)

        val entries = mutableListOf<Entry>()
        val seenNames = mutableMapOf<String, KSClassDeclaration>()
        var hasErrors = false

        for (cls in valid) {
            val annotation = cls.annotations.first {
                it.shortName.asString() == "CodecType"
            }

            val rawName = annotation.arguments
                .first { it.name?.asString() == "name" }
                .value as String

            val instantiable = annotation.arguments
                .first { it.name?.asString() == "instantiable" }
                .value as Boolean

            val typeName = rawName.ifBlank { cls.simpleName.asString() }

            if (cls.qualifiedName == null) {
                logger.error("Could not resolve qualified name for ${cls.simpleName.asString()}", cls)
                hasErrors = true
                continue
            }

            if (instantiable) {
                val hasNoArgCtor = cls.primaryConstructor?.parameters?.all { it.hasDefault } == true
                        || cls.getConstructors().any { ctor ->
                    ctor.parameters.isEmpty() || ctor.parameters.all { it.hasDefault }
                }

                if (!hasNoArgCtor) {
                    logger.error(
                        "Class ${cls.qualifiedName!!.asString()} is annotated with @CodecType(instantiable = true) but has no no-arg constructor. " +
                                "Add a no-arg constructor, provide default values for all constructor parameters, or set instantiable = false.",
                        cls
                    )
                    hasErrors = true
                    continue
                }
            }

            val existing = seenNames[typeName]
            if (existing != null) {
                logger.error(
                    "Duplicate @CodecType name \"$typeName\" on ${cls.qualifiedName!!.asString()}. " +
                            "Already registered by ${existing.qualifiedName!!.asString()}.",
                    cls
                )
                hasErrors = true
                continue
            }

            seenNames[typeName] = cls
            entries += Entry(typeName, cls, instantiable)
        }

        check(!hasErrors) { "CodecTypeAnnotationProcessor failed due to errors above." }

        if (entries.isEmpty()) return deferred

        generateRegistrationFile(entries.map { Triple(it.typeName, it.cls, it.instantiable) }, valid.map { it.containingFile!! }.toList())

        logger.info("Generated $fileName with ${entries.size} codec(s): ${entries.map { it.typeName }}")
        return deferred
    }

    private fun generateRegistrationFile(
        entries: List<Triple<String, KSClassDeclaration, Boolean>>,
        sourceFiles: List<KSFile>
    ) {
        val registerAllFun = FunSpec.builder("registerAll")
            .also { func ->
                for ((typeName, cls, instantiable) in entries) {
                    val className = ClassName(
                        cls.packageName.asString(),
                        cls.simpleName.asString()
                    )
                    if (instantiable) {
                        func.addStatement(
                            "%T.register(\ntypeName·=·%S,\nclazz·=·%T::class,\ninstantiator·=·{·%T()·}\n)",
                            codecTypeRegistryClass,
                            typeName,
                            className,
                            className
                        )
                    } else {
                        func.addStatement(
                            "%T.register(\ntypeName·=·%S,\nclazz·=·%T::class\n)",
                            codecTypeRegistryClass,
                            typeName,
                            className
                        )
                    }
                }
            }
            .build()

        val objectSpec = TypeSpec.objectBuilder(fileName)
            .addFunction(registerAllFun)
            .build()

        val fileSpec = FileSpec.builder(packageName, fileName)
            .addFileComment("AUTO-GENERATED by CodecTypeAnnotationProcessor — do not edit")
            .addType(objectSpec)
            .build()

        val dependencies = Dependencies(aggregating = true, *sourceFiles.toTypedArray())

        try {
            val file = environment.codeGenerator.createNewFile(
                dependencies,
                packageName = packageName,
                fileName = fileName,
                extensionName = "kt"
            )
            file.bufferedWriter().use { fileSpec.writeTo(it) }
        } catch (_: Exception) {
            // ignore duplicate file generation on incremental builds
        }
    }
}



