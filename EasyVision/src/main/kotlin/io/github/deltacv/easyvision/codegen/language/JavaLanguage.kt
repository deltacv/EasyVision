package io.github.deltacv.easyvision.codegen.language

import io.github.deltacv.easyvision.codegen.Visibility
import io.github.deltacv.easyvision.codegen.build.*
import io.github.deltacv.easyvision.codegen.csv

object JavaLanguage : Language {

    override fun instanceVariableDeclaration(
        vis: Visibility,
        name: String,
        variable: Value,
        isStatic: Boolean,
        isFinal: Boolean
    ): String {
        val modifiers = if(isStatic) " static" else "" +
                if(isFinal) " final" else ""

        val ending = if(variable.value != null) "= ${variable.value};" else ";"

        return "${vis.name.lowercase()}$modifiers ${variable.type.shortNameWithGenerics} $name $ending"
    }

    override fun localVariableDeclaration(name: String, variable: Value): String {
        val ending = if(variable.value != null) "= ${variable.value};" else ";"

        return "${variable.type.shortNameWithGenerics} $name $ending"
    }

    override fun variableSetDeclaration(name: String, v: Value) = "$name = ${v.value!!};"

    override fun instanceVariableSetDeclaration(name: String, v: Value) = "this.$name = ${v.value!!};"

    override fun methodCallDeclaration(className: Type, methodName: String, vararg parameters: Value) =
        "${className.shortName}.$methodName(${parameters.csv()});"

    override fun methodCallDeclaration(methodName: String, vararg parameters: Value) =
        "$methodName(${parameters.csv()});"

    override fun methodDeclaration(
        vis: Visibility,
        returnType: Type,
        name: String,
        vararg parameters: Parameter,
        isStatic: Boolean,
        isFinal: Boolean,
        isOverride: Boolean
    ): Pair<String?, String> {
        val static = if(isStatic) "static " else ""
        val final = if(isFinal) "final " else ""

        return Pair(if(isOverride) {
            "@Override"
        } else null,
            "${vis.name.lowercase()} $static$final${returnType.shortName} $name(${parameters.csv()})"
        )
    }

    override fun foreachLoopDeclaration(variable: Value, iterable: Value) =
        "for(${variable.type.shortName} ${variable.value} : ${iterable.value})"

    override fun whileLoopDeclaration(condition: Condition) = "while(${condition.value})"

    override fun classDeclaration(
        vis: Visibility,
        name: String,
        body: Scope,
        extends: Type?,
        implements: Array<Type>?,
        isStatic: Boolean,
        isFinal: Boolean
    ): String {
        val static = if(isStatic) "static " else ""
        val final = if(isFinal) "final " else ""

        val e = if(extends != null) "extends ${extends.shortNameWithGenerics} " else ""
        val i = if(implements?.isNotEmpty() == true) "implements ${implements.csv()} " else ""

        return "${vis.name.lowercase()} $static${final}class $name $e$i"
    }

    override fun enumClassDeclaration(name: String, vararg values: String) = "enum $name { ${values.csv() } "

    override fun new(type: Type, vararg parameters: Value) = Value(
        type, "new ${type.shortName}${if(type.hasGenerics) "<>" else ""}(${parameters.csv()})"
    )

    override fun callValue(methodName: String, returnType: Type, vararg parameters: Value) = Value(
        returnType, "$methodName(${parameters.csv()})"
    )

}