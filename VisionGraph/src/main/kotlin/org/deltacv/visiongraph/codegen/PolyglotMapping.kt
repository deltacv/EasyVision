package org.deltacv.visiongraph.codegen

import org.deltacv.visiongraph.codegen.language.Language
import kotlin.reflect.KClass
import kotlin.reflect.full.superclasses

sealed interface PolyglotMapping {
    data class LanguageInsts(val languages: List<Language>) : PolyglotMapping {
        override fun match(language: Language) =
            languages.mapNotNull { it::class.inheritanceDistance(language) }.minOrNull() ?: -1
    }

    data class LanguageClasses(val languageClasses: List<KClass<out Language>>) : PolyglotMapping {
        override fun match(language: Language) =
            languageClasses.mapNotNull { it.inheritanceDistance(language) }.minOrNull() ?: -1
    }

    object AnyLanguage : PolyglotMapping {
        override fun match(language: Language) = Int.MAX_VALUE // worst possible match, will be used if no other mapping matches
    }

    /**
     * @returns Inheritance distance if matches, -1 otherwise. 0 means exact match, 1 means direct superclass, etc.
     */
    fun match(language: Language): Int
}

private fun KClass<out Language>.inheritanceDistance(other: Language): Int? {
    if(this == other::class) return 0

    val target = this
    var current: KClass<*>? = other::class
    var distance = 0

    while (current != null) {
        if (current == target) return distance
        current = current.superclasses.firstOrNull()
        distance++
    }

    return null
}



