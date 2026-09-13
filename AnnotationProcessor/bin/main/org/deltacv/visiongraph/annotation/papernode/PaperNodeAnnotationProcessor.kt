/*
 * PaperVision
 * Copyright (C) 2026 Sebastian Erives, deltacv
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.deltacv.visiongraph.annotation.papernode

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*

class PaperNodeAnnotationProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val logger = environment.logger

    private val moduleName = environment.options["moduleName"] ?: ""
    private val packageName: String = environment.options["paperNodeClassesMetadataPackage"] ?: "org.deltacv.visiongraph.node.generated"
    private val fileName: String = moduleName + (environment.options["paperNodeClassesMetadataClassName"] ?: "PaperNodeMetadata")

    private val paperNodeRegistryClass = ClassName(
        "org.deltacv.visiongraph.node",
        "PaperNodeRegistry"
    )

    private val nodeCategoryClass = ClassName(
        "org.deltacv.visiongraph.node",
        "NodeCategory"
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("org.deltacv.visiongraph.node.PaperNode")
            .filterIsInstance<KSClassDeclaration>()

        val deferred = symbols.filterNot { it.validate() }.toList()
        val valid = symbols.filter { it.validate() }

        generateRegistrationFile(valid)

        return deferred
    }

    private fun generateRegistrationFile(classes: Sequence<KSClassDeclaration>) {
        data class Entry(val className: ClassName, val category: String, val instantiable: Boolean)

        val entries = mutableListOf<Entry>()
        var hasErrors = false

        for (cls in classes) {
            if (cls.qualifiedName == null) {
                logger.error("Could not resolve qualified name for ${cls.simpleName.asString()}", cls)
                hasErrors = true
                continue
            }

            val annotation = cls.annotations.first { it.shortName.asString() == "PaperNode" }
            val categoryDecl = annotation.arguments
                .first { it.name?.asString() == "category" }
                .value as KSClassDeclaration
                
            val instantiable = annotation.arguments
                .first { it.name?.asString() == "instantiable" }
                .value as Boolean

            if (instantiable) {
                val hasNoArgCtor =
                    cls.primaryConstructor?.parameters?.all { it.hasDefault } == true ||
                    cls.getConstructors().any { ctor ->
                        ctor.parameters.isEmpty() || ctor.parameters.all { it.hasDefault }
                    }

                if (!hasNoArgCtor) {
                    logger.error(
                        "Class ${cls.qualifiedName!!.asString()} is annotated with @PaperNode(instantiable = true) " +
                        "but has no no-arg constructor. Add a no-arg constructor, provide " +
                        "default values for all constructor parameters, or set instantiable = false.",
                        cls
                    )
                    hasErrors = true
                    continue
                }
            }

            entries += Entry(
                ClassName(cls.packageName.asString(), cls.simpleName.asString()),
                categoryDecl.simpleName.asString(),
                instantiable
            )
        }

        if (hasErrors) return

        if (entries.isEmpty()) return

        val sourceFiles = classes.map { it.containingFile!! }.toList()

        val registerAllFun = FunSpec.builder("registerAll")
            .also { func ->
                for ((className, category, instantiable) in entries) {
                    if (instantiable) {
                        func.addStatement(
                            "%T.registerNode(%T::class, %T.%L) { %T() }",
                            paperNodeRegistryClass,
                            className,
                            nodeCategoryClass,
                            category,
                            className
                        )
                    } else {
                        func.addStatement(
                            "%T.registerNode(%T::class, %T.%L)",
                            paperNodeRegistryClass,
                            className,
                            nodeCategoryClass,
                            category
                        )
                    }
                }
            }
            .build()

        val objectSpec = TypeSpec.objectBuilder(fileName)
            .addFunction(registerAllFun)
            .build()

        val fileSpec = FileSpec.builder(packageName, fileName)
            .addFileComment("AUTO-GENERATED by PaperNodeAnnotationProcessor — do not edit")
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
