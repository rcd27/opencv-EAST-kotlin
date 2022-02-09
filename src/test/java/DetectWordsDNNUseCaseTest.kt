import org.junit.jupiter.api.Test
import org.opencv.core.Mat
import org.opencv.highgui.HighGui
import org.opencv.imgcodecs.Imgcodecs

/*
see: - https://github.com/argman/EAST
     - https://github.com/opencv/opencv_extra/blob/master/testdata/dnn/download_models.py#L354
     - https://gist.github.com/berak/788da80d1dd5bade3f878210f45d6742
     - Python source: https://github.com/spmallick/learnopencv/blob/master/TextDetectionEAST/textDetection.py
 */

class DetectWordsDNNUseCaseTest : OpenCVTest {

    @Test
    fun testUseCase() {
        val inputImage: Mat =
            Imgcodecs.imread("./src/test/resources/test-screenshot.png")

        val result = DetectWordsDNNUseCase.detectWords(inputImage)

        HighGui.namedWindow("Result")
        HighGui.imshow("Result", result)
        HighGui.waitKey()
    }
}
