package org.deltacv.visiongraph.codegen

typealias PolyglotGeneratorMutableMap<I, S> = MutableMap<PolyglotMapping, Generator<I, S>>
typealias PolyglotGeneratorMap<I, S> = Map<PolyglotMapping, Generator<I, S>>

interface PolyglotGenerator<I, S: CodeGenSession> : Generator<I, S> {
    val generators: PolyglotGeneratorMap<I, S>

    override fun genCode(input: I, current: CodeGen.Current): S {
        var currentCloseness = Int.MAX_VALUE
        var closestGenerator: Generator<I, S>? = null

        for((mapping, gen) in generators) {
            // closeness < 0 is complete discard, 0 is perfect match, higher is worse match
            val closeness = mapping.match(current.language)

            if(closeness < 0) continue // dont even consider it

            if(closeness == 0) {
                closestGenerator = gen
                break
            }

            if((closeness > 0 && closestGenerator == null) || (closeness <= currentCloseness)) {
                currentCloseness = closeness
                closestGenerator = gen
            }
        }

        return closestGenerator?.genCode(input, current) ?: throw NoSuchElementException(
            "No generator found for language ${current.language.javaClass.simpleName} at node ${this::class.simpleName}"
        )
    }
}



