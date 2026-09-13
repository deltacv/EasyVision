/*
 * VisionGraph
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

package org.deltacv.visiongraph.codegen.build.language.jvm

import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.Visibility
import org.deltacv.visiongraph.codegen.build.AccessorVariable
import org.deltacv.visiongraph.codegen.build.Parameter
import org.deltacv.visiongraph.codegen.build.DeclarableVariable
import org.deltacv.visiongraph.codegen.dsl.jvm.jvmTargets

fun CodeGen.Current.enableJavaTargets() = this {
    if(!codeGen.hasFlag("targetsEnabled")) {
        jvmTargets(enableTargetsIfNeeded = false) {
            val forceCasts = codeGen.isForPreviz

            scope {
                swapTargets()
            }

            group {
                private(frontRectTargets)
                private(frontRotRectTargets)

                separate()

                private(backRectTargets)
                private(backRotRectTargets)
            }

            codeGen.classEndScope {
                val labelParameter = Parameter(JavaTypes.String, "label")
                val rectTargetParameter = Parameter(JvmOpenCv.Rect, "rect")
                val rotatedRectTargetParameter = Parameter(JvmOpenCv.RotatedRect, "rotRect")

                method(
                    Visibility.PRIVATE,
                    VoidType,
                    "swapTargets",
                    isSynchronized = true
                ) {
                    val tempRect = DeclarableVariable("tempRect", frontRectTargets)
                    local(tempRect)

                    frontRectTargets instanceSet backRectTargets
                    backRectTargets instanceSet tempRect

                    separate()

                    val tempRotRect = DeclarableVariable("tempRotRect", frontRotRectTargets)
                    local(tempRotRect)

                    frontRotRectTargets instanceSet backRotRectTargets
                    backRotRectTargets instanceSet tempRotRect

                    separate()

                    backRectTargets("clear")
                    backRotRectTargets("clear")
                }

                separate()

                method(
                    Visibility.PRIVATE,
                    VoidType,
                    "addRectTarget",
                    labelParameter,
                    rectTargetParameter
                ) {
                    backRectTargets("put", labelParameter, rectTargetParameter)
                }

                method(
                    Visibility.PRIVATE,
                    VoidType, "addRotRectTarget",
                    labelParameter,
                    rotatedRectTargetParameter
                ) {
                    backRotRectTargets("put", labelParameter, rotatedRectTargetParameter)
                }

                separate()

                method(Visibility.PUBLIC, JvmOpenCv.Rect, "getRectTarget", labelParameter,  isSynchronized = true) {
                    returnMethod(frontRectTargets.callValue("get", JvmOpenCv.Rect, labelParameter).castTo(JvmOpenCv.Rect, forceCasts))
                }

                separate()

                method(Visibility.PUBLIC, JavaTypes.List(JvmOpenCv.Rect), "getRectTargets", labelParameter,  isSynchronized = true) {
                    // get all the rect targets that start with label
                    val targets = DeclarableVariable("targets", JavaTypes.ArrayList(JvmOpenCv.Rect).new())
                    local(targets)

                    separate()

                    val entryType = JavaTypes.Map.Entry(JavaTypes.String, JvmOpenCv.Rect)
                    val entrySetType = JavaTypes.Set(entryType)
                    foreach(AccessorVariable(entryType, "namedTarget"), frontRectTargets.callValue("entrySet", entrySetType)) {
                        val label = it.callValue("getKey", JavaTypes.String).castTo(JavaTypes.String, forceCasts)

                        ifCondition(label.callValue("startsWith", BooleanType, labelParameter).condition()) {
                            targets("add", it.callValue("getValue", JvmOpenCv.Rect).castTo(JvmOpenCv.Rect, forceCasts))
                        }
                    }

                    separate()

                    returnMethod(targets)
                }

                separate()

                method(Visibility.PUBLIC, JvmOpenCv.RotatedRect, "getRotRectTarget", labelParameter, isSynchronized = true) {
                    returnMethod(
                        frontRotRectTargets.callValue("get", JvmOpenCv.RotatedRect, labelParameter)
                            .castTo(JvmOpenCv.RotatedRect, forceCasts)
                    )
                }

                separate()

                method(Visibility.PUBLIC, JavaTypes.List(JvmOpenCv.RotatedRect), "getRotRectTargets", labelParameter, isSynchronized = true) {
                    // get all the rect targets that start with label
                    val targets = DeclarableVariable("targets", JavaTypes.ArrayList(JvmOpenCv.RotatedRect).new())
                    local(targets)

                    separate()

                    val entryType = JavaTypes.Map.Entry(JavaTypes.String, JvmOpenCv.RotatedRect)
                    val entrySetType = JavaTypes.Set(entryType)

                    foreach(DeclarableVariable(entryType, "namedTarget"), frontRotRectTargets.callValue("entrySet", entrySetType)) {
                        val label = it.callValue("getKey", JavaTypes.String).castTo(JavaTypes.String, forceCasts)
                        ifCondition(label.callValue("startsWith", BooleanType, labelParameter).condition()) {
                            targets("add", it.callValue("getValue", JvmOpenCv.RotatedRect).castTo(JvmOpenCv.RotatedRect, forceCasts))
                        }
                    }

                    separate()

                    returnMethod(targets)
                }

                separate()
            }
        }

        codeGen.addFlag("targetsEnabled")
    }
}



