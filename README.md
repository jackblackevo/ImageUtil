ImageUtil
=========
## Features
* 等比例調整圖片大小
* 旋轉圖片
* 格式轉換

### Supported Image Formats
#### Import
* GIF
* JPEG
* PNG
* BMP
* TIFF
* PDF

#### Export
* GIF
* JPEG
* PNG
* BMP
* TIFF
* Base64 String

## Usage
1. 編輯 config.properties 設定檔
  * 可調整參數 from 為 image 或 pdf
  * 可調整參數 image.output.format（輸出圖片格式）
  * 可調整參數 image.output.width（輸出圖片寬）、image.output.height（輸出圖片高）
  * 可調整參數 image.output.quality（輸出圖片品質，範圍為 0.0~1.0）
  * 可調整參數 image.output.orientation（portrait 為輸出直立圖片、landscape 為輸出橫向圖片）
  * 可調整參數 image.output.multipage（true 為單檔多頁 TIFF、false 為多檔 TIFF）
2. 將要轉檔的圖片放置到 image_source 目錄中
3. 執行 run.sh（macOS、Linux）或 run.bat（Windows）
4. 顯示完成後，輸出圖片會放置於 output 目錄中
