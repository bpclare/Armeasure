// Checklists: the measurements a kind of item needs, in the order to take them. A car seat is the first; free
// measuring has none (each measurement named as it's taken). AR walks the checklist: after each one is saved, the next
// not yet measured comes up.
package com.bpclare.armeasure.data

data class Step(val name: String, val hint: String, val kind: Kind = Kind.LINE)

data class Template(val id: String, val name: String, val steps: List<Step>)

object Templates {
    val CAR_SEAT = Template(
        "car_seat", "Car seat",
        listOf(
            Step("Back height", "From the seam where the back meets the cushion, straight up to the top of the back"),
            Step("Back width, top", "Across the top of the back, side to side"),
            Step("Back width, bottom", "Across the back just above the cushion"),
            Step("Back thickness", "At the side: the front face of the back to its rear"),
            Step("Back, over the top", "Trace from the seam, up the front, over the top, down to the back's bottom edge", Kind.PATH),
            Step("Cushion width", "Across the cushion at its widest, bolster to bolster"),
            Step("Cushion depth", "From the front edge back to the seam"),
            Step("Cushion, over the front", "Trace from the seam, along the top, round the front edge, down to the bottom", Kind.PATH),
            Step("Bolster height", "A side bolster: its top down to the flat of the cushion"),
            Step("Headrest width", "Across the headrest at its widest"),
            Step("Headrest height", "Bottom of the headrest to its top"),
            Step("Headrest posts apart", "Center of one post to the center of the other"),
        ),
    )

    val BOX = Template(
        "box", "Box or object (W × D × H)",
        listOf(
            Step("Width", "Side to side"),
            Step("Depth", "Front to back"),
            Step("Height", "Bottom to top"),
        ),
    )

    val all = listOf(CAR_SEAT, BOX)

    fun byId(id: String?): Template? = all.find { it.id == id }

    fun isStep(t: Template?, name: String) = t?.steps?.any { it.name.equals(name, ignoreCase = true) } == true

    fun measured(item: Item, step: Step): Measurement? = item.measurements.find { it.name.equals(step.name, ignoreCase = true) }

    /** The first step of the item's checklist not yet measured, or null. */
    fun nextStep(item: Item): Step? = byId(item.template)?.steps?.firstOrNull { measured(item, it) == null }

    /** A free measurement's name: Measurement N, the first N not already used. */
    fun defaultName(item: Item): String {
        var n = item.measurements.size + 1
        while (item.measurements.any { it.name.equals("Measurement $n", ignoreCase = true) }) n++
        return "Measurement $n"
    }
}
