package fr.axllvy.inase

@JsFun("() => Date.now()")
private external fun jsNowMs(): Double

actual fun nowMs(): Long = jsNowMs().toLong()
