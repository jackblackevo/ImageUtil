package idv.jackblackevo.util;

import com.twelvemonkeys.image.ResampleOp;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.bind.DatatypeConverter;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ImageUtil {
  private enum Orientation {
    Portrait, Landscape
  }

  public static final Orientation PORTRAIT = Orientation.Portrait;
  public static final Orientation LANDSCAPE = Orientation.Landscape;

  public static class Builder {
    private static final String OUTPUT_PREFIX = "output_";
    private static final String RESIZE_PREFIX = "resize_";
    private static final String COMBINE_PREFIX = "combine_";

    private List<File> imageFileList;
    private List<String> imageTypeList;
    private List<BufferedImage> bufferedImageList;

    private Builder(List<File> imageFileList, List<String> imagesTypeList, List<BufferedImage> bufferedImageList) {
      this.imageFileList = imageFileList;
      this.imageTypeList = imagesTypeList;
      this.bufferedImageList = bufferedImageList;
    }

    public Builder resize(int width, int height) throws IOException {
      if (width <= 0 || height <= 0) {
        return this;
      }

      List<File> newImageFileList = new ArrayList<>();
      List<String> newImageTypeList = new ArrayList<>();
      List<BufferedImage> newBufferedImageList = new ArrayList<>();

      Iterator<File> imageFileListIterator = imageFileList.iterator();
      Iterator<String> imageTypeIterator = imageTypeList.iterator();
      Iterator<BufferedImage> bufferedImageIterator = bufferedImageList.iterator();
      while (bufferedImageIterator.hasNext()) {
        File imageFile = imageFileListIterator.next();
        String imageType = imageTypeIterator.next();
        BufferedImage image = bufferedImageIterator.next();

        File resizedImageFile = new File(imageFile.getParent() + File.separator + RESIZE_PREFIX + imageFile.getName());
        newImageFileList.add(resizedImageFile);

        newImageTypeList.add(imageType);

        Dimension newImageSize = getScaledDimension(new Dimension(image.getWidth(), image.getHeight()), new Dimension(width, height));
        BufferedImageOp reSampler = new ResampleOp(newImageSize.width, newImageSize.height, ResampleOp.FILTER_LANCZOS);
        BufferedImage resizedImage = reSampler.filter(image, null);

        newBufferedImageList.add(resizedImage);
      }

      return new Builder(newImageFileList, newImageTypeList, newBufferedImageList);
    }

    public Builder rotate(Orientation orientation) {
      List<File> newImageFileList = new ArrayList<>();
      List<String> newImageTypeList = new ArrayList<>();
      List<BufferedImage> newBufferedImageList = new ArrayList<>();

      Iterator<File> imageFileListIterator = imageFileList.iterator();
      Iterator<String> imageTypeIterator = imageTypeList.iterator();
      Iterator<BufferedImage> bufferedImageIterator = bufferedImageList.iterator();
      while (bufferedImageIterator.hasNext()) {
        File imageFile = imageFileListIterator.next();
        String imageType = imageTypeIterator.next();
        BufferedImage image = bufferedImageIterator.next();

        newImageFileList.add(imageFile);

        newImageTypeList.add(imageType);

        int rotate;
        if (orientation == PORTRAIT) {
          rotate = image.getWidth() > image.getHeight() ? 90 : 0;
        } else {
          rotate = image.getWidth() < image.getHeight() ? 90 : 0;
        }

        if (rotate != 0) {
          AffineTransform affine = new AffineTransform();
          affine.setToRotation(Math.toRadians(rotate), image.getHeight() / 2, image.getHeight() / 2);

          AffineTransformOp op = new AffineTransformOp(affine, AffineTransformOp.TYPE_BICUBIC);
          BufferedImage rotatedImage = new BufferedImage(image.getHeight(), image.getWidth(), image.getType());
          op.filter(image, rotatedImage);

          newBufferedImageList.add(rotatedImage);
        } else {
          newBufferedImageList.add(image);
        }
      }

      return new Builder(newImageFileList, newImageTypeList, newBufferedImageList);
    }

    public File writeToMultipageTIFF(String destLocation) throws IOException {
      return writeToMultipageTIFF(destLocation, -1);
    }

    public File writeToMultipageTIFF(File destLocation) throws IOException {
      return writeToMultipageTIFF(destLocation, -1);
    }

    public File writeToMultipageTIFF(String destLocation, float quality) throws IOException {
      return writeToMultipageTIFF(new File(destLocation), quality);
    }

    public File writeToMultipageTIFF(File destLocation, float quality) throws IOException {
      if (!destLocation.exists()) {
        destLocation.mkdirs();
      } else if (!destLocation.isDirectory()) {
        throw new UnsupportedOperationException("Destination location is not a directory!");
      }

      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_hh_mm_ss");
      File newFile = new File(destLocation.getPath() + File.separator + OUTPUT_PREFIX + COMBINE_PREFIX + sdf.format(Calendar.getInstance().getTime()) + ".tiff");

      ImageWriter imageWriter = getImageWriter("TIFF", ImageIO.createImageOutputStream(newFile));

      ImageWriteParam params = imageWriter.getDefaultWriteParam();
      params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      params.setCompressionType("JPEG");
      if (quality >= 0 && quality <= 1) {
        params.setCompressionQuality(quality);
      }

      imageWriter.prepareWriteSequence(null);
      Iterator<BufferedImage> imageIterator = bufferedImageList.iterator();
      while (imageIterator.hasNext()) {
        IIOImage iioImage = new IIOImage(imageIterator.next(), null, null);
        imageWriter.writeToSequence(iioImage, params);
      }
      imageWriter.endWriteSequence();

      return newFile;
    }

    public List<File> writeToFiles(String destLocation) throws IOException {
      return writeToFiles(destLocation, null);
    }

    public List<File> writeToFiles(File destLocation) throws IOException {
      return writeToFiles(destLocation, null);
    }

    public List<File> writeToFiles(String destLocation, String fileType) throws IOException {
      return writeToFiles(destLocation, fileType, -1);
    }

    public List<File> writeToFiles(File destLocation, String fileType) throws IOException {
      return writeToFiles(destLocation, fileType, -1);
    }

    public List<File> writeToFiles(String destLocation, String fileType, float quality) throws IOException {
      return writeToFiles(new File(destLocation), fileType, quality);
    }

    public List<File> writeToFiles(File dest, String fileType, float quality) throws IOException {
      if (!dest.exists()) {
        dest.mkdirs();
      } else if (!dest.isDirectory()) {
        throw new UnsupportedOperationException("Destination location is not a directory!");
      }

      List<File> newImageFileList = new ArrayList<>();
//      List<String> newImageTypeList = new ArrayList<>();
//      List<BufferedImage> newBufferedImageList = new ArrayList<>();

      Iterator<File> imageFileListIterator = imageFileList.iterator();
      Iterator<String> imageTypeIterator = imageTypeList.iterator();
      Iterator<BufferedImage> bufferedImageIterator = bufferedImageList.iterator();
      while (bufferedImageIterator.hasNext()) {
        File imageFile = imageFileListIterator.next();
        String targetType = imageTypeIterator.next();
        BufferedImage image = bufferedImageIterator.next();

        String fileName;
        if (fileType == null || "".equals(fileType)) {
          fileName = imageFile.getName();
        } else {
          targetType = fileType;
          fileName = imageFile.getName().replaceFirst("[^.]+$", targetType.toLowerCase());
        }

        IIOImage iioImage = new IIOImage(image, null, null);

//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        ImageWriter baosWriter = getImageWriter(targetType, ImageIO.createImageOutputStream(baos));
//        ImageWriteParam baosWriteParam = baosWriter.getDefaultWriteParam();
//        if ("TIFF".equalsIgnoreCase(fileType) && baosWriteParam.canWriteCompressed()) {
//          baosWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
//          baosWriteParam.setCompressionType("JPEG");
//        }
//        baosWriter.write(null, iioImage, baosWriteParam);

        File destTIFFFile = new File(dest.getPath() + File.separator + OUTPUT_PREFIX + fileName);
        ImageWriter fileWriter = getImageWriter(targetType, ImageIO.createImageOutputStream(destTIFFFile));
        ImageWriteParam fileWriterParam = fileWriter.getDefaultWriteParam();
        if ("TIFF".equalsIgnoreCase(fileType) && fileWriterParam.canWriteCompressed()) {
          fileWriterParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
          fileWriterParam.setCompressionType("JPEG");
          if (quality >= 0 && quality <= 1) {
            fileWriterParam.setCompressionQuality(quality);
          }
        }
        fileWriter.write(null, iioImage, fileWriterParam);

        newImageFileList.add(destTIFFFile);
//
//        newImageTypeList.add(targetType);
//
//        InputStream in = new ByteArrayInputStream(baos.toByteArray());
//        BufferedImage outputImage = ImageIO.read(in);
//        newBufferedImageList.add(outputImage);
      }

//      return new Builder(newImageFileList, newImageTypeList, newBufferedImageList);
      return newImageFileList;
    }

    public List<String> convertToBase64() {
      List<String> base64StringList = new ArrayList<>();

      return convertImageToBase64String(this);
    }
  }

  public static Builder fromSrc(String src, String... srcs) {
    File imageSource = new File(src);
    File[] imageSources = new File[srcs.length];
    for (int i = 0; i < srcs.length; i++) {
      imageSources[i] = new File(srcs[i]);
    }

    return fromSrc(imageSource, imageSources);
  }

  public static Builder fromSrc(File imageFile, File... imageFiles) {
    List<File> imageFileList = new ArrayList(Arrays.asList(imageFiles));
    imageFileList.add(0, imageFile);

    Builder builder = null;
    try {
      builder = getImagesDetail(imageFileList);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return builder;
  }

  public static Builder fromPDF(String PDFSrc) {
    File PDFFile = new File(PDFSrc);

    return fromPDF(PDFFile);
  }

  public static Builder fromPDF(File PDFFile) {
    if (!PDFFile.exists() || PDFFile.isDirectory()) {
      throw new UnsupportedOperationException("Illegal PDF format!");
    }

    Builder imagesDetail = null;
    try (
      PDDocument document = PDDocument.load(PDFFile)
    ) {
      PDFRenderer pdfRenderer = new PDFRenderer(document);

      List<File> newImageFileList = new ArrayList<>();
      List<String> newImageTypeList = new ArrayList<>();
      List<BufferedImage> newBufferedImageList = new ArrayList<>();

      for (int i = 0; i < document.getNumberOfPages(); i++) {
        int pageNo = i + 1;
        File imageFile = new File(PDFFile.getParent() + File.separator + PDFFile.getName().replaceFirst("\\.[^.]+$", "_p" + pageNo + ".tiff"));
        newImageFileList.add(imageFile);

        newImageTypeList.add("TIFF");

        BufferedImage image = pdfRenderer.renderImageWithDPI(i, 150, ImageType.RGB);
        newBufferedImageList.add(image);
      }

      imagesDetail = new Builder(newImageFileList, newImageTypeList, newBufferedImageList);
    } catch (InvalidPasswordException e) {
      e.printStackTrace();

      return imagesDetail;
    } catch (IOException e) {
      e.printStackTrace();

      return imagesDetail;
    }

    return imagesDetail;
  }

  public static String convertImageToBase64String(String src) {
    File file = new File(src);

    return convertImageToBase64String(file);
  }

  public static String convertImageToBase64String(File src) {
    String base64String = "";

    if (!src.exists()) {
      return base64String;
    }

    try {
      Builder imagesDetail = getImagesDetail(new File[]{src});
      List<String> base64StringList = convertImageToBase64String(imagesDetail);

      base64String = base64StringList.get(0);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return base64String;
  }

  private static List<String> convertImageToBase64String(Builder imagesDetail) {
    List<String> base64StringList = new ArrayList<>();

    List<BufferedImage> bufferedImageList = imagesDetail.bufferedImageList;
    List<String> imageTypeList = imagesDetail.imageTypeList;

    Iterator<BufferedImage> bufferedImageIterator = bufferedImageList.iterator();
    Iterator<String> imageTypeIterator = imageTypeList.iterator();
    while (bufferedImageIterator.hasNext()) {
      BufferedImage image = bufferedImageIterator.next();
      String imageType = imageTypeIterator.next();

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try {
        ImageIO.write(image, imageType, output);
      } catch (IOException e) {
        e.printStackTrace();
      }

      String base64String = DatatypeConverter.printBase64Binary(output.toByteArray());
      base64StringList.add(base64String);
    }

    return base64StringList;
  }

  private static Builder getImagesDetail(File[] files) throws IOException {
    List<File> fileList = Arrays.asList(files);

    return getImagesDetail(fileList);
  }

  private static Builder getImagesDetail(List<File> fileList) throws IOException {
    List<File> imageFileList = new ArrayList<>();
    List<String> imageTypeList = new ArrayList<>();
    List<BufferedImage> bufferedImageList = new ArrayList<>();

    Iterator<File> filesIterator = fileList.iterator();
    while (filesIterator.hasNext()) {
      File imageFile = filesIterator.next();
      try {
        if (imageFile.isDirectory()) {
          if (imageFile.listFiles().length == 0) {
            continue;
          }
          Builder tempImagesDetail = getImagesDetail(imageFile.listFiles());

          imageFileList.addAll(tempImagesDetail.imageFileList);
          imageTypeList.addAll(tempImagesDetail.imageTypeList);
          bufferedImageList.addAll(tempImagesDetail.bufferedImageList);
        } else if (imageFile.exists()) {
          ImageReader imageReader = getImageReader(imageFile);

          imageFileList.add(imageFile);
          imageTypeList.add(imageReader.getFormatName());
          bufferedImageList.add(imageReader.read(imageReader.getMinIndex()));
        }
      } catch (UnsupportedOperationException e) {
        System.out.println(e.getMessage() + " Skipped file: " + imageFile.getPath());

        continue;
      }
    }

    if (imageFileList.isEmpty()) {
      throw new UnsupportedOperationException("No image!");
    }

    return new Builder(imageFileList, imageTypeList, bufferedImageList);
  }

  private static ImageReader getImageReader(File file) throws IOException {
    List<ImageReader> imageReaderList = getImageReaderList(new File[]{file});

    return imageReaderList.get(0);
  }

  private static List<ImageReader> getImageReaderList(File[] files) throws IOException {
    InputStream[] iss = new InputStream[files.length];
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      iss[i] = new FileInputStream(file);
    }

    return getImageReaderList(iss);
  }

  private static List<ImageReader> getImageReaderList(InputStream[] iss) throws IOException {
    List<ImageReader> imageReaderList = new ArrayList<>();
    for (int i = 0; i < iss.length; i++) {
      InputStream is = iss[i];
      ImageInputStream iis = ImageIO.createImageInputStream(is);
      // Test
//      ImageIO.scanForPlugins();
      Iterator<ImageReader> imageReaderIterator = ImageIO.getImageReaders(iis);

      if (!imageReaderIterator.hasNext()) {
        throw new UnsupportedOperationException("No image reader found!");
      }

      ImageReader imageReader = imageReaderIterator.next();
      imageReader.setInput(iis);

      imageReaderList.add(imageReader);
    }

    return imageReaderList;
  }

  private static ImageWriter getImageWriter(String imageType, ImageOutputStream ios) throws IOException {
    Iterator<ImageWriter> imageWriterIterator = ImageIO.getImageWritersByFormatName(imageType);

    if (!imageWriterIterator.hasNext()) {
      throw new UnsupportedOperationException("No image writer found!");
    }
    ImageWriter imageWriter = imageWriterIterator.next();
    imageWriter.setOutput(ios);

    return imageWriter;
  }

  private static Dimension getScaledDimension(Dimension imageSize, Dimension boundary) {
    double widthRatio = boundary.getWidth() / imageSize.getWidth();
    double heightRatio = boundary.getHeight() / imageSize.getHeight();
    double ratio = Math.min(widthRatio, heightRatio);

    return new Dimension((int) (imageSize.width * ratio), (int) (imageSize.height * ratio));
  }
}
