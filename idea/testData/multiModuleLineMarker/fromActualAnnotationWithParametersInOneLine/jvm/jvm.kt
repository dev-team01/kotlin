actual annotation class <lineMarker descr="Has declaration in common module">Ann</lineMarker>(
        actual val <lineMarker descr="Has declaration in common module">x</lineMarker>: Int, actual val y: String
)

// TODO: first marker on 'Ann' is generated twice here, see collectSlowLineMarkers main loop.
// Since it's fragile place, I don't want to fix it right now


/*
LINEMARKER: Has declaration in common module
TARGETS:
common.kt
expect annotation class <1>Ann(
*//*
LINEMARKER: Has declaration in common module
TARGETS:
common.kt
expect annotation class <1>Ann(
*//*
LINEMARKER: Has declaration in common module
TARGETS:
common.kt
        val <1>x: Int, val <2>y: String
*/

