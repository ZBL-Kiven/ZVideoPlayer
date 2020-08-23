package com.zj.player.img.scale.easing

@Suppress("unused")
enum class ScaleEffect(val easing: Easing) {
    BACK(Back()), BOUNCE(Bounce()), CUBIC(Cubic()), Circle(Circle()), ELASTIC(Elastic()), EXPO(Expo()), LINEAR(Linear()), QUAD(Quad()), QUART(Quart()), QUINT(Quint()), SINE(Sine())
}
