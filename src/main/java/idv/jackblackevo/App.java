package idv.jackblackevo;

import idv.jackblackevo.util.ImageUtil;

import java.net.URL;
import java.util.Properties;

public class App {
  public static void main(String[] args) throws Exception {
    URL cpProp = Thread.currentThread().getContextClassLoader().getResource("config.properties");
    Properties prop = new Properties();
    prop.load(cpProp.openStream());

    String from = prop.getProperty("from");
    String imageInput = prop.getProperty("image.input.folder.path");
    String PDFInput = prop.getProperty("pdf.input.file.path");
    String exportImageFormat = prop.getProperty("image.output.format");
    String imageOutput = prop.getProperty("image.output.folder.path");

    int imageTargetWidth = Integer.parseInt(prop.getProperty("image.output.width"));
    int imageTargetHeight = Integer.parseInt(prop.getProperty("image.output.height"));
    float imageTargetQuality = Float.parseFloat(prop.getProperty("image.output.quality"));

    String imageOrientation = prop.getProperty("image.output.orientation");

    boolean imageTargetMultipage = Boolean.parseBoolean(prop.getProperty("image.output.multipage"));

    ImageUtil.Builder imageBuilder;
    if ("PDF".equalsIgnoreCase(from)) {
      imageBuilder = ImageUtil.fromPDF(PDFInput);
    } else {
      imageBuilder = ImageUtil.fromSrc(imageInput);
    }

    if ("landscape".equalsIgnoreCase(imageOrientation)) {
      imageBuilder = imageBuilder.rotate(ImageUtil.LANDSCAPE);
    } else {
      imageBuilder = imageBuilder.rotate(ImageUtil.PORTRAIT);
    }

    imageBuilder = imageBuilder.resize(imageTargetWidth, imageTargetHeight);

    if (imageTargetMultipage) {
      imageBuilder.writeToMultipageTIFF(imageOutput, imageTargetQuality);
    } else {
      imageBuilder.writeToFiles(imageOutput, exportImageFormat, imageTargetQuality);
    }

    System.out.println("Done!");
  }
}
