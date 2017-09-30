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

    private boolean isClosed = false;
    private List<ImageData> imageDataList;

    private Builder(List<ImageData> imageDataList) {
      this.imageDataList = imageDataList;
    }

    public void close() {
      if (isClosed) {
        throw new UnsupportedOperationException("Builder is closed!");
      }

      Iterator<ImageData> imageDetailListIterator = imageDataList.iterator();
      while (imageDetailListIterator.hasNext()) {
        ImageData imageData = imageDetailListIterator.next();
        BufferedImage[] imagePages = imageData.getImagePages();

        int numImagePages = imagePages.length;
        for (int i = 0; i < numImagePages; i++) {
          BufferedImage imagePage = imagePages[i];

          // 釋放內部緩衝的記憶體
          imagePage.flush();
        }
      }

      isClosed = true;
    }

    public boolean checkIsClosed() {
      return isClosed;
    }

    public Builder resize(int width, int height) throws IOException {
      if (isClosed) {
        throw new UnsupportedOperationException("Builder is closed!");
      }

      if (width <= 0 || height <= 0) {
        return this;
      }

      Iterator<ImageData> imageDetailListIterator = imageDataList.iterator();
      while (imageDetailListIterator.hasNext()) {
        ImageData imageData = imageDetailListIterator.next();
        String fileName = imageData.getFileName();
        String imageType = imageData.getImageType();
        BufferedImage[] imagePages = imageData.getImagePages();

        int numImagePages = imagePages.length;
        BufferedImage[] newImagePages = new BufferedImage[numImagePages];
        for (int i = 0; i < numImagePages; i++) {
          BufferedImage imagePage = imagePages[i];

          Dimension newImageSize = getScaledDimension(new Dimension(imagePage.getWidth(), imagePage.getHeight()), new Dimension(width, height));
          BufferedImageOp reSampler = new ResampleOp(newImageSize.width, newImageSize.height, ResampleOp.FILTER_LANCZOS);
          BufferedImage resizedImagePage = reSampler.filter(imagePage, null);

          // 釋放內部緩衝的記憶體
          imagePage.flush();

          newImagePages[i] = resizedImagePage;
        }

        imageData.setFileName(RESIZE_PREFIX + fileName);
        imageData.setImageType(imageType);
        imageData.setImagePages(newImagePages);
      }

      return this;
    }

    public Builder rotate(Orientation orientation) {
      if (isClosed) {
        throw new UnsupportedOperationException("Builder is closed!");
      }

      Iterator<ImageData> imageDetailListIterator = imageDataList.iterator();
      while (imageDetailListIterator.hasNext()) {
        ImageData imageData = imageDetailListIterator.next();
        String fileName = imageData.getFileName();
        String imageType = imageData.getImageType();
        BufferedImage[] imagePages = imageData.getImagePages();

        int numImagePages = imagePages.length;
        BufferedImage[] newImagePages = new BufferedImage[numImagePages];
        for (int i = 0; i < numImagePages; i++) {
          BufferedImage imagePage = imagePages[i];

          int rotate;
          if (orientation == PORTRAIT) {
            rotate = imagePage.getWidth() > imagePage.getHeight() ? 90 : 0;
          } else {
            rotate = imagePage.getWidth() < imagePage.getHeight() ? 90 : 0;
          }

          if (rotate != 0) {
            AffineTransform affine = new AffineTransform();
            double theta = Math.toRadians(rotate);
            double anchor = imagePage.getHeight() / 2d;
            affine.setToRotation(theta, anchor, anchor);

            AffineTransformOp op = new AffineTransformOp(affine, AffineTransformOp.TYPE_BICUBIC);
            BufferedImage rotatedImagePage = new BufferedImage(imagePage.getHeight(), imagePage.getWidth(), imagePage.getType());
            op.filter(imagePage, rotatedImagePage);

            // 釋放內部緩衝的記憶體
            imagePage.flush();

            newImagePages[i] = rotatedImagePage;
          } else {
            newImagePages[i] = imagePage;
          }
        }

        imageData.setFileName(fileName);
        imageData.setImageType(imageType);
        imageData.setImagePages(newImagePages);
      }

      return this;
    }

    public File writeToMultipageTIFF(String destLocation, boolean isCloseBuilderAfterWrote) throws IOException {
      return writeToMultipageTIFF(destLocation, -1, isCloseBuilderAfterWrote);
    }

    public File writeToMultipageTIFF(File destLocation, boolean isCloseBuilderAfterWrote) throws IOException {
      return writeToMultipageTIFF(destLocation, -1, isCloseBuilderAfterWrote);
    }

    public File writeToMultipageTIFF(String destLocation, float quality, boolean isCloseBuilderAfterWrote) throws IOException {
      return writeToMultipageTIFF(new File(destLocation), quality, isCloseBuilderAfterWrote);
    }

    public File writeToMultipageTIFF(File destLocation, float quality, boolean isCloseBuilderAfterWrote) throws IOException {
      if (isClosed) {
        throw new UnsupportedOperationException("Builder is closed!");
      }

      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_hh_mm_ss");
      File newFile = new File(destLocation.getPath() + File.separator + OUTPUT_PREFIX + COMBINE_PREFIX + sdf.format(Calendar.getInstance().getTime()) + ".tiff");

      ImageWriter imageWriter = null;
      try (
        ImageOutputStream ios = ImageIO.createImageOutputStream(newFile)
      ) {
        imageWriter = getImageWriter("TIFF", ios);

        ImageWriteParam params = imageWriter.getDefaultWriteParam();
        setImageWriteParamCompression(params, quality);

        imageWriter.prepareWriteSequence(null);
        Iterator<ImageData> imageDetailIterator = imageDataList.iterator();
        while (imageDetailIterator.hasNext()) {
          BufferedImage[] imagePages = imageDetailIterator.next().getImagePages();
          for (int i = 0; i < imagePages.length; i++) {
            IIOImage iioImage = new IIOImage(imagePages[i], null, null);
            imageWriter.writeToSequence(iioImage, params);
          }
        }
        imageWriter.endWriteSequence();
      } catch (IOException e) {
        if (imageWriter != null) {
          imageWriter.abort();
        }

        throw e;
      } finally {
        if (imageWriter != null) {
          imageWriter.dispose();
        }
      }

      if (isCloseBuilderAfterWrote) {
        close();
      }

      return newFile;
    }

    public List<File> writeToFiles(String destLocation, boolean isCloseBuilderAfterWrote) throws IOException {
      return writeToFiles(destLocation, null, isCloseBuilderAfterWrote);
    }

    public List<File> writeToFiles(File destLocation, boolean isCloseBuilderAfterWrote) throws IOException {
      return writeToFiles(destLocation, null, isCloseBuilderAfterWrote);
    }

    public List<File> writeToFiles(String destLocation, String fileType, boolean isCloseBuilderAfterWrote) throws IOException {
      return writeToFiles(destLocation, fileType, -1, isCloseBuilderAfterWrote);
    }

    public List<File> writeToFiles(File destLocation, String fileType, boolean isCloseBuilderAfterWrote) throws IOException {
      return writeToFiles(destLocation, fileType, -1, isCloseBuilderAfterWrote);
    }

    public List<File> writeToFiles(String destLocation, String fileType, float quality, boolean isCloseBuilderAfterWrote) throws IOException {
      return writeToFiles(new File(destLocation), fileType, quality, isCloseBuilderAfterWrote);
    }

    public List<File> writeToFiles(File destLocation, String fileType, float quality, boolean isCloseBuilderAfterWrote) throws IOException {
      if (isClosed) {
        throw new UnsupportedOperationException("Builder is closed!");
      }

      List<File> newImageFileList = new ArrayList<>();

      Iterator<ImageData> imageDetailListIterator = imageDataList.iterator();
      while (imageDetailListIterator.hasNext()) {
        ImageData imageData = imageDetailListIterator.next();
        String imageType = imageData.getImageType();
        BufferedImage[] imagePages = imageData.getImagePages();

        if (fileType != null && !"".equals(fileType)) {
          imageType = fileType;
        }

        int numImagePages = imagePages.length;

        boolean isTIFF = "TIF".equalsIgnoreCase(imageType) || "TIFF".equalsIgnoreCase(imageType);
        if (isTIFF || "GIF".equalsIgnoreCase(imageType)) {
          File destFile = new File(destLocation.getPath() + File.separator + OUTPUT_PREFIX + imageData.getFileName() + "." + imageType);

          writeImage(destFile, imageType, quality, imagePages);

          newImageFileList.add(destFile);
        } else {
          for (int i = 0; i < numImagePages; i++) {
            String page = "_p" + (i + 1);
            if (numImagePages == 1) {
              page = "";
            }

            File pageDestFile = new File(destLocation.getPath() + File.separator + OUTPUT_PREFIX + imageData.getFileName() + page + "." + imageType);
            BufferedImage imagePage = imagePages[i];

            writeImage(pageDestFile, imageType, quality, imagePage);

            newImageFileList.add(pageDestFile);
          }
        }
      }

      if (isCloseBuilderAfterWrote) {
        close();
      }

      return newImageFileList;
    }

    public List<String[]> convertToBase64() {
      if (isClosed) {
        throw new UnsupportedOperationException("Builder is closed!");
      }

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

  public static String[] convertImageToBase64String(String src) {
    File file = new File(src);

    return convertImageToBase64String(file);
  }

  public static String[] convertImageToBase64String(File src) {
    String[] base64String = new String[]{};

    if (!src.exists()) {
      return base64String;
    }

    try {
      Builder imagesDetail = getImagesDetail(new File[]{src});
      List<String[]> base64StringList = convertImageToBase64String(imagesDetail);

      base64String = base64StringList.get(0);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return base64String;
  }

  private static List<String[]> convertImageToBase64String(Builder imagesDetail) {
    List<String[]> base64StringList = new ArrayList<>();

    List<ImageData> imageDataList = new ArrayList<>();

    Iterator<ImageData> imageDetailListIterator = imageDataList.iterator();
    while (imageDetailListIterator.hasNext()) {
      ImageData imageData = imageDetailListIterator.next();
      String imageType = imageData.getImageType();
      BufferedImage[] imagePages = imageData.getImagePages();

      int numImagePages = imagePages.length;
      String[] base64StringPages = new String[numImagePages];
      for (int i = 0; i < numImagePages; i++) {
        BufferedImage imagePage = imagePages[i];

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
          ImageIO.write(imagePage, imageType, output);
        } catch (IOException e) {
          e.printStackTrace();
        }

        String base64StringPage = DatatypeConverter.printBase64Binary(output.toByteArray());
        base64StringPages[i] = base64StringPage;
      }
      base64StringList.add(base64StringPages);
    }

    return base64StringList;
  }

  private static Builder getImagesDetail(File[] files) throws IOException {
    List<File> fileList = Arrays.asList(files);

    return getImagesDetail(fileList);
  }

  private static Builder getImagesDetail(List<File> fileList) throws IOException {
    List<ImageData> imageDataList = new ArrayList<>();

    Iterator<File> filesIterator = fileList.iterator();
    while (filesIterator.hasNext()) {
      File imageFile = filesIterator.next();
      try {
        if (imageFile.isDirectory()) {
          if (imageFile.listFiles().length == 0) {
            continue;
          }
          Builder tempImagesDetail = getImagesDetail(imageFile.listFiles());

          imageDataList.addAll(tempImagesDetail.imageDataList);
        } else if (imageFile.exists()) {
          ImageData imageData = readImage(imageFile);
          imageDataList.add(imageData);
        }
      } catch (UnsupportedOperationException e) {
        // PDF
        try (
          PDDocument document = PDDocument.load(imageFile)
        ) {
          PDFRenderer pdfRenderer = new PDFRenderer(document);

          int numPDFPages = document.getNumberOfPages();
          BufferedImage[] imagePages = new BufferedImage[numPDFPages];
          for (int i = 0; i < numPDFPages; i++) {
            BufferedImage imagePage = pdfRenderer.renderImageWithDPI(i, 150, ImageType.RGB);
            imagePages[i] = imagePage;
          }

          ImageData imageData = new ImageData(imageFile.getName().replaceFirst("\\.[^.]+$", ""), "TIFF", imagePages);
          imageDataList.add(imageData);
        } catch (InvalidPasswordException ee) {
          System.out.println(e.getMessage() + " Try to read as PDF...");
          System.out.println(ee.getMessage() + " Skipped file: " + imageFile.getPath());
        } catch (IOException ee) {
          System.out.println(e.getMessage() + " Try to read as PDF...");
          System.out.println(ee.getMessage() + " Skipped file: " + imageFile.getPath());
        }

        continue;
      }
    }

    if (imageDataList.isEmpty()) {
      throw new UnsupportedOperationException("No image!");
    }

    return new Builder(imageDataList);
  }

  private static ImageData readImage(File imageFile) throws IOException {
    String fileName = imageFile.getName().replaceFirst("\\.[^.]+$", "");
    String formatName;
    BufferedImage[] imagePages;

    ImageReader imageReader = null;
    try (
      FileInputStream fis = new FileInputStream(imageFile);
    ) {
      imageReader = getImageReader(fis);

      formatName = imageReader.getFormatName();

      // 取得多頁圖片頁數
      int numImagePages = imageReader.getNumImages(true);
      imagePages = new BufferedImage[numImagePages];
      for (int i = 0; i < numImagePages; i++) {
        BufferedImage imagePage = imageReader.read(i);
        imagePages[i] = imagePage;
      }
    } catch (IOException e) {
      if (imageReader != null) {
        imageReader.abort();
      }

      throw e;
    } finally {
      if (imageReader != null) {
        imageReader.dispose();
      }
    }

    return new ImageData(fileName, formatName, imagePages);
  }

  private static void writeImage(File destFile, String imageType, float quality, BufferedImage imagePage) throws IOException {
    writeImage(destFile, imageType, quality, new BufferedImage[]{imagePage});
  }

  private static void writeImage(File destFile, String imageType, float quality, BufferedImage[] imagePages) throws IOException {
    File destLocation = destFile.getParentFile();
    if (!destLocation.exists()) {
      if (destLocation.mkdirs()) {
        throw new IIOException("Can not create destination directory!");
      }
    } else if (!destLocation.isDirectory()) {
      throw new UnsupportedOperationException("Destination location is not a directory!");
    }

    boolean isWriteMultipage = imagePages.length > 1;
    boolean isTIFF = "TIF".equalsIgnoreCase(imageType) || "TIFF".equalsIgnoreCase(imageType);

    ImageWriter imageWriter = null;
    try (
      ImageOutputStream ios = ImageIO.createImageOutputStream(destFile)
    ) {
      imageWriter = getImageWriter(imageType, ios);

      ImageWriteParam imageWriteParam = imageWriter.getDefaultWriteParam();
      if (isTIFF && imageWriteParam.canWriteCompressed()) {
        setImageWriteParamCompression(imageWriteParam, quality);
      }

      if (!isWriteMultipage) {
        BufferedImage imagePage = imagePages[0];
        IIOImage iioImage = new IIOImage(imagePage, null, null);
        imageWriter.write(null, iioImage, imageWriteParam);
      } else if (isTIFF || "GIF".equalsIgnoreCase(imageType)) {
        int numImagePages = imagePages.length;

        imageWriter.prepareWriteSequence(null);
        for (int i = 0; i < numImagePages; i++) {
          BufferedImage imagePage = imagePages[i];

          IIOImage iioImage = new IIOImage(imagePage, null, null);
          imageWriter.writeToSequence(iioImage, imageWriteParam);
        }
        imageWriter.endWriteSequence();
      } else {
        throw new UnsupportedOperationException("Only support to wirte the mulitpage file with TIFF or GIF");
      }
    } catch (IOException e) {
      if (imageWriter != null) {
        imageWriter.abort();
      }

      throw e;
    } finally {
      if (imageWriter != null) {
        imageWriter.dispose();
      }
    }
  }

  private static ImageReader getImageReader(FileInputStream fis) throws IOException {
    List<ImageReader> imageReaderList = getImageReaderList(new InputStream[]{fis});
    return imageReaderList.get(0);
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

  private static void setImageWriteParamCompression(ImageWriteParam writerParam, float quality) {
    writerParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    writerParam.setCompressionType("JPEG");
    if (quality >= 0 && quality <= 1) {
      writerParam.setCompressionQuality(quality);
    }
  }

  private static Dimension getScaledDimension(Dimension imageSize, Dimension boundary) {
    double widthRatio = boundary.getWidth() / imageSize.getWidth();
    double heightRatio = boundary.getHeight() / imageSize.getHeight();
    double ratio = Math.min(widthRatio, heightRatio);

    return new Dimension((int) (imageSize.width * ratio), (int) (imageSize.height * ratio));
  }
}
