package com.example

import org.junit.Test
import java.io.File

class ExampleUnitTest {
  @Test
  fun testBraces() {
    val file1 = File("src/main/java/com/example/ui/screens/HabayebScreen.kt")
    val file2 = File("../app/src/main/java/com/example/ui/screens/HabayebScreen.kt")
    val file = if (file1.exists()) file1 else file2
    if (!file.exists()) {
        println("ERROR: HabayebScreen.kt not found!")
        return
    }
    val lines = file.readLines()
    var level = 0
    var inString = false
    var inMultilineComment = false

    for ((index, line) in lines.withIndex()) {
        val lineNum = index + 1
        var i = 0
        var linePrinted = false
        while (i < line.length) {
            val char = line[i]
            if (inMultilineComment) {
                if (char == '*' && i + 1 < line.length && line[i + 1] == '/') {
                    inMultilineComment = false
                    i++
                }
            } else if (inString) {
                if (char == '\\') {
                    i++
                } else if (char == '"') {
                    inString = false
                }
            } else {
                if (char == '/' && i + 1 < line.length && line[i + 1] == '*') {
                    inMultilineComment = true
                    i++
                } else if (char == '/' && i + 1 < line.length && line[i + 1] == '/') {
                    break
                } else if (char == '"') {
                    inString = true
                } else if (char == '{') {
                    level++
                    println("Line $lineNum: { -> Level $level | ${line.trim()}")
                } else if (char == '}') {
                    level--
                    println("Line $lineNum: } -> Level $level | ${line.trim()}")
                }
            }
            i++
        }
    }
    println("Final brace level: $level")
  }
}
