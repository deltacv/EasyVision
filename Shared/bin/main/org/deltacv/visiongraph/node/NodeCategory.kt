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

package org.deltacv.visiongraph.node

import org.deltacv.visiongraph.gui.style.CurrentStyles.imnodesStyle
import org.deltacv.visiongraph.gui.style.hexColor

enum class NodeCategory(val properName: String,
                        val color: Int = imnodesStyle.titleBar,
                        val colorSelected: Int = imnodesStyle.titleBarHovered) {

    FLOW("cat_pipeline_flow",
        hexColor("#00838f"), // material cyan
        hexColor("#00acc1")),

    CODE("cat_coding"),
    HIGH_LEVEL_CV("cat_high_level_cv"),

    IMAGE_PROC("cat_image_proc",
        hexColor("#ff6f00"), // material amber
        hexColor("#ffa000")),

    FEATURE_DET("cat_feature_det",
        hexColor("#283593"), // material indigo 800
        hexColor("#3949ab")), // material indigo 600

    CLASSIFICATION("cat_class",
        hexColor("#6a1b9a"), // material purple 800
        hexColor("#8e24aa")), // material purple 600

    OVERLAY("cat_overlay",
        hexColor("#00695c"), // material teal 800
        hexColor("#00897b")),  // material teal 600

    TRANSFORM("cat_transform",
        hexColor("#c62828"), // material red 800
        hexColor("#d32f2f")), // material red 600

    MATH("cat_math",
        hexColor("#2e7d32"), // material green 800
        hexColor("#43A047")), // material green 600),
    MISC("cat_misc")

}



