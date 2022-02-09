import org.opencv.core.*
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object DetectWordsDNNUseCase {

    fun detectWords(inputImage: Mat): Mat {
        val net: Net = Dnn.readNetFromTensorflow("./src/main/resources/EAST/frozen_east_text_detection.pb")

        Imgproc.cvtColor(inputImage, inputImage, Imgproc.COLOR_RGBA2RGB)

        val resizedToKernel = Mat()
        Imgproc.resize(inputImage, resizedToKernel, Size(640.0, 640.0))

        // Output geometry
        val size = Size(640.0, 640.0) // it is 320 in python example
        val w: Int = (size.width / 4).roundToInt()
        // the geometry has 4 vertically stacked maps, the score is 1
        val h: Int = (size.height / 4).roundToInt()

        // This mean can be found in different places and examples in the internet and seems to be "stable"
        val mean = Scalar(123.68, 116.78, 103.94)

        val blob: Mat = Dnn.blobFromImage(resizedToKernel, 1.0, size, mean, true, false)
        net.setInput(blob)
        val outs = arrayListOf<Mat?>(null, null)
        val outNames = listOf("feature_fusion/Conv_7/Sigmoid", "feature_fusion/concat_3")
        net.forward(outs, outNames)

        // Decode predicted bounding boxes
        val scores: Mat = outs[0]!!.reshape(1, h)
        // see: http://answers.opencv.org/question/175676/javaandroid-access-4-dim-mat-planes/
        val geometry = outs[1]!!.reshape(1, 5 * h)
        val confidenceList = arrayListOf<Float>()
        val boxesList = decode(scores, geometry, confidenceList, 0.9f)

        // Apply non-maximum suppression procedure
        val confidences = MatOfFloat(Converters.vector_float_to_Mat(confidenceList))
        val boxesArray: Array<RotatedRect> = boxesList.toTypedArray() // FIXME: attention
        val boxes = MatOfRotatedRect(*boxesArray)

        val indices = MatOfInt()
        Dnn.NMSBoxesRotated(boxes, confidences, 0.5f, 0.4f, indices)

        // Render detections
        val ratio = Point(resizedToKernel.cols() / size.width, resizedToKernel.rows() / size.height)
        val indexes: IntArray = indices.toArray()
        for (i in indexes.indices) {
            val rotated = boxesArray[indexes[i]]
            val vertices: Array<Point?> = arrayOf(null, null, null, null)
            rotated.points(vertices)
            for (j in 0 until 4) {
                vertices[j]!!.x *= ratio.x
                vertices[j]!!.y *= ratio.y
            }
            for (j in 0 until 4) {
                Imgproc.line(
                    resizedToKernel,
                    vertices[j],
                    vertices[(j + 1) % 4],
                    Scalar(0.0, 0.0, 255.0),
                    1
                )
            }
        }

        return resizedToKernel
    }

    private fun decode(
        scores: Mat,
        geometry: Mat,
        confidences: MutableList<Float>,
        scoreThresh: Float
    ): List<RotatedRect> {
        val w: Int = geometry.cols()
        val h: Int = geometry.rows() / 5 // TODO: figure out magic number

        val result = arrayListOf<RotatedRect>()

        for (y in 0 until h) {
            val scoresData = scores.row(y)
            val x0Data = geometry.submat(0, h, 0, w).row(y)
            val x1Data = geometry.submat(h, 2 * h, 0, w).row(y)
            val x2Data = geometry.submat(2 * h, 3 * h, 0, w).row(y)
            val x3Data = geometry.submat(3 * h, 4 * h, 0, w).row(y)
            val anglesData = geometry.submat(4 * h, 5 * h, 0, w).row(y)

            for (x in 0 until w) {
                val score: Double = scoresData.get(0, x)[0]
                if (score >= scoreThresh) {
                    val offsetX = x * 4.0
                    val offsetY = y * 4.0
                    val angle = anglesData[0, x][0]
                    val cosA = cos(angle)
                    val sinA = sin(angle)
                    val x0 = x0Data[0, x][0]
                    val x1 = x1Data[0, x][0]
                    val x2 = x2Data[0, x][0]
                    val x3 = x3Data[0, x][0]
                    val h1 = x0 + x2
                    val w1 = x1 + x3
                    val offset = Point(
                        offsetX + cosA * x1 + sinA * x2,
                        offsetY - sinA * x1 + cosA * x2
                    )
                    val p1 = Point(-1 * sinA * h1 + offset.x, -1 * cosA * h1 + offset.y)
                    val p3 = Point(
                        -1 * cosA * w1 + offset.x,
                        sinA * w1 + offset.y
                    ) // original trouble here !

                    val r = RotatedRect(
                        Point(0.5 * (p1.x + p3.x), 0.5 * (p1.y + p3.y)),
                        Size(w1, h1),
                        -1 * angle * 180 / Math.PI
                    )
                    result.add(r)
                    confidences.add(score.toFloat())
                }
            }

        }

        return result
    }
}