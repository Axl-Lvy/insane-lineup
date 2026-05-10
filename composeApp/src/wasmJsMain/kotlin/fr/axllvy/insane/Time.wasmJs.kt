package fr.axllvy.insane

@JsFun("() => Date.now()")
private external fun jsNowMs(): Double

actual fun nowMs(): Long = jsNowMs().toLong()
