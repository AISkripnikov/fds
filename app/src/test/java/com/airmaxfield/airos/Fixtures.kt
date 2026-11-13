package com.airmaxfield.airos

fun fixture(path: String): String =
    requireNotNull(Thread.currentThread().contextClassLoader!!.getResource("fixtures/$path")) { "no fixture $path" }.readText()
