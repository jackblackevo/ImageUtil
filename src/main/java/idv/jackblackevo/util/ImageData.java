package idv.jackblackevo.util;

import java.awt.image.BufferedImage;
import java.io.File;

class ImageData {
  private String fileName;
  private String imageType;
  private BufferedImage[] imagePages;

  public ImageData(String fileName, String imageType, BufferedImage[] imagePages) {
    this.fileName = fileName;
    this.imageType = imageType;
    this.imagePages = imagePages;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getImageType() {
    return imageType;
  }

  public void setImageType(String imageType) {
    this.imageType = imageType;
  }

  public BufferedImage[] getImagePages() {
    return imagePages;
  }

  public void setImagePages(BufferedImage[] imagePages) {
    this.imagePages = imagePages;
  }
}
