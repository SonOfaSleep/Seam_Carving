package seamcarving

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class SeamCarving(val bufferedImage: BufferedImage, val desiredWidth: Int, val desiredHeight: Int, val output: File) {
    val reduceWidth = bufferedImage.width > desiredWidth
    val reduceHeight = bufferedImage.height > desiredHeight

    init {
        println("Working on new width...")
        val newImageWidthModified = verticalSemModify(bufferedImage, desiredWidth, reduceWidth)

        println("Working on new height...")
        val newImageHeightModified = verticalSemModify(imageTranspose(newImageWidthModified), desiredHeight, reduceHeight)

        println("Writing")
        ImageIO.write(imageTranspose(newImageHeightModified), "png", output)
        println("Done")
    }

    fun verticalSemModify(image: BufferedImage, desiredWidth: Int, reduce: Boolean): BufferedImage {
        if (image.width == desiredWidth) return image

        val energyMatrix = getEnergyMatrix(image)
        val semSumMatrix = semPathSum(energyMatrix)
        val pathMap = shortestPathIndices(semSumMatrix)

        val newWidth = if (reduce) image.width - 1 else image.width + 1
        val newImage = BufferedImage(newWidth, image.height, BufferedImage.TYPE_INT_RGB)

        // map (key = row[y]; value = column[x]) indices

        for (y in 0 until image.height) {
            for (x in 0 until newImage.width) {
                // if (reduce && pathMap[y] == x) continue // showing seams in end image when reducing
                
                // if reducing we need next index in original image and vice versa
                val reducedX = if (reduce) x + 1 else x - 1

                val newX = if (x > pathMap[y]!!) reducedX else x

                newImage.setRGB(x, y, image.getRGB(newX, y))
            }
        }
        return verticalSemModify(newImage, desiredWidth, reduce)
    }
    fun imageTranspose(origImage: BufferedImage): BufferedImage {
        val transImage = BufferedImage(origImage.height, origImage.width, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until transImage.width) {
            for (y in 0 until transImage.height) {
                transImage.setRGB(x, y, origImage.getRGB(y, x))
            }
        }
        return transImage
    }
    fun shortestPathIndices(matrix: List<MutableList<Double>>): MutableMap<Int, Int> {
        val pathIndicesList = mutableMapOf<Int, Int>() // (key = row; value = column) indices

        // finding element in last row with the lowest energy and writing it coordinates
        var forCheckEnergy = Double.MAX_VALUE
        val last = matrix.lastIndex

        for (elementIndex in matrix[last].indices) {
            val energy = matrix[last][elementIndex]
            if (energy < forCheckEnergy) {
                forCheckEnergy = energy
                pathIndicesList[last] = elementIndex
            }
        }

        // traversing all rows from penultimate to first
        for (rowIndex in last - 1 downTo 0) {
            val prev = pathIndicesList[rowIndex + 1] // element index of previous pixel
            pathIndicesList[rowIndex] = prev!!

            // looking on left pixel if his energy is smaller, writing his index
            if (prev > 0 && matrix[rowIndex][prev - 1] < matrix[rowIndex][pathIndicesList[rowIndex]!!]) {
                pathIndicesList[rowIndex] = prev - 1
            }
            // looking on the right pixel energy
            if (prev < matrix[0].lastIndex && matrix[rowIndex][prev + 1] < matrix[rowIndex][pathIndicesList[rowIndex]!!]) {
                pathIndicesList[rowIndex] = prev + 1
            }
        }
        return pathIndicesList
    }
    fun semPathSum(matrix: List<MutableList<Double>>): List<MutableList<Double>> {
        // creating new matrix and filling it with path sum for finding the shortest path (https://habr.com/ru/post/48518/)
        val pathSumMatrix = MutableList(matrix.size){ MutableList(matrix[0].size) { 0.0 } }

        // initializing first row it will be "as is" for every matrix
        pathSumMatrix[0] = matrix[0]

        for (rowIndex in matrix.indices) {
            if (rowIndex == 0) continue
            for (elementIndex in matrix[rowIndex].indices) {
                val initialSum = matrix[rowIndex][elementIndex]

                // writing path sum depending on plath in a row
                when (elementIndex) {
                    0 -> {
                        val abovePixel = pathSumMatrix[rowIndex - 1][elementIndex]
                        val rightPixel = pathSumMatrix[rowIndex - 1][elementIndex + 1]

                        pathSumMatrix[rowIndex][elementIndex] = initialSum + minOf(abovePixel, rightPixel)
                    }
                    matrix[0].lastIndex -> {
                        val abovePixel = pathSumMatrix[rowIndex - 1][elementIndex]
                        val leftPixel = pathSumMatrix[rowIndex - 1][elementIndex - 1]

                        pathSumMatrix[rowIndex][elementIndex] = initialSum + minOf(abovePixel, leftPixel)
                    }
                    else -> {
                        val abovePixel = pathSumMatrix[rowIndex - 1][elementIndex]
                        val leftPixel = pathSumMatrix[rowIndex - 1][elementIndex - 1]
                        val rightPixel = pathSumMatrix[rowIndex - 1][elementIndex + 1]

                        pathSumMatrix[rowIndex][elementIndex] = initialSum + minOf(abovePixel, leftPixel, rightPixel)
                    }
                }
            }
        }
        return pathSumMatrix
    }
    fun getEnergyMatrix(bufferedImage: BufferedImage): List<MutableList<Double>> {
        //finding energy of every pixel and maxEnergyValue, filling matrixOfEnergy with energy values
        var maxEnergyValue = 0.0
        val matrix = List(bufferedImage.height){ mutableListOf<Double>() }
        for (y in 0 until bufferedImage.height) {
            for (x in 0 until bufferedImage.width) {
                val energy = pixelEnergy(x, y, bufferedImage)
                if (energy > maxEnergyValue) maxEnergyValue = energy

                matrix[y].add(energy)
            }
        }
        return matrix
    }
    fun pixelEnergy(x:Int, y: Int, image: BufferedImage): Double {
        return sqrt(gradientX(x, y, image) + gradientY(x, y, image))
    }
    fun gradientX(x: Int, y: Int, image: BufferedImage): Double {
        val (xLeft, xRight) = when (x) {
            0 -> listOf(x, x + 2)
            image.width - 1 -> listOf(x - 2, x)
            else -> listOf(x - 1, x + 1)
        }

        val leftPixelColors = Color(image.getRGB(xLeft, y))
        val rightPixelColors = Color(image.getRGB(xRight, y))
        val leftRGB = listOf(leftPixelColors.red, leftPixelColors.green, leftPixelColors.blue)
        val rightRGB = listOf(rightPixelColors.red, rightPixelColors.green, rightPixelColors.blue)

        var xGradient: Double = 0.00
        for (index in 0..2) {
            xGradient += abs(leftRGB[index] - rightRGB[index]).toDouble().pow(2)
        }
        return xGradient
    }
    fun gradientY(x: Int, y: Int, image: BufferedImage): Double {
        val (yUp, yDown) = when (y) {
            0 -> listOf(y, y + 2)
            image.height - 1 -> listOf(y - 2, y)
            else -> listOf(y - 1, y + 1)
        }

        val upPixelColors = Color(image.getRGB(x, yUp))
        val downPixelColors = Color(image.getRGB(x, yDown))
        val upRGB = listOf(upPixelColors.red, upPixelColors.green, upPixelColors.blue)
        val downRGB = listOf(downPixelColors.red, downPixelColors.green, downPixelColors.blue)

        var yGradient: Double = 0.00
        for (index in 0..2) {
            yGradient += abs(upRGB[index] - downRGB[index]).toDouble().pow(2)
        }
        return yGradient
    }
}

fun main(args: Array<String>) {
    println("Hello to Seam Carving program!\n")

    val inputFilePath = if (args.contains("-in")) {
        args[args.indexOf("-in") + 1]
    } else {
        println("Enter file path:")
        readLine()!!
    }
    val saveFilePath = if (args.contains("-out")) {
        args[args.indexOf("-out") + 1]
    } else {
        println("Enter output file path:")
        readLine()!!
    }

    val bufferedImage = ImageIO.read(File(inputFilePath))
    println("Your image is ${bufferedImage.width} width and ${bufferedImage.height} height.")
    println("Enter desired width (digits only!):")
    val desiredWidth = readLine()!!.toInt()
    println("Enter desired height (digits only!):")
    val desiredHeight = readLine()!!.toInt()
    val seam = SeamCarving(bufferedImage, desiredWidth, desiredHeight, File(saveFilePath))
    println("Bye!")
}