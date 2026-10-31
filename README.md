# 海拔位置 App（Android）

顯示：目前時間（時:分）、海拔高度、所在地區（縣市＋鄉鎮區）、經緯度。

## 編譯方式一：Android Studio
1. 解壓縮後，用 Android Studio「Open」打開 `AltitudeApp` 資料夾
2. 等 Gradle 同步完成
3. 手機開啟「開發人員選項 → USB 偵錯」，接上電腦按 ▶ Run
   或選 Build → Build App Bundle(s)/APK(s) → Build APK(s)，取得 `app-debug.apk`

## 編譯方式二：不裝 Android Studio（GitHub 雲端編譯）
1. 在 GitHub 建一個新 repository，把整個資料夾內容上傳
2. 到 repository 的「Actions」分頁，等「Build APK」跑完（約 3–5 分鐘）
3. 點進該次執行，在 Artifacts 下載 `AltitudeApp-apk`，解壓得到 APK
4. 傳到手機安裝（需允許「安裝未知來源應用程式」）

## 說明
- 海拔來自 GPS，請在室外或窗邊使用；剛開啟時誤差較大，等幾十秒會變準。
- 已自動把 GPS 橢球高換算成「海平面高度」，與地圖上的海拔一致。
- 地區名稱需要網路（使用手機內建 Geocoder）。
- 需要 Android 7.0 以上。
