package com.tap.n64.input

enum class N64Button(
    val mask: Int,
) {
    A(0x8000),
    B(0x4000),
    Z(0x2000),
    Start(0x1000),
    DpadUp(0x0800),
    DpadDown(0x0400),
    DpadLeft(0x0200),
    DpadRight(0x0100),
    L(0x0020),
    R(0x0010),
    CUp(0x0008),
    CDown(0x0004),
    CLeft(0x0002),
    CRight(0x0001),
}
