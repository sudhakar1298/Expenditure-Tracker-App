# GPay & SMS Expenditure Tracker App

An Android application designed to track your monthly expenditures dynamically by reading transaction notifications (from Google Pay) and bank transaction SMS alerts. 

## Features

- **Automated GPay Tracking**: Push notifications from Google Pay (GPay) are intercepted, parsed, and logged automatically.
- **SMS Draft Confirmations**: Banking SMS debit alerts (such as Indian Bank format) are intercepted, parsed, and added to the app as a "Draft." The app alerts you under **Pending Confirmations**, prompting you to verify/edit the merchant name, select a category, and save the transaction.
- **Google Sheets Real-Time Sync**: Finalized transactions are instantly synchronized to a Google Sheet via a Google Apps Script Web App webhook.
- **Local Sandbox Storage**: Uses a Room database to cache your transactions locally on the device, ensuring the app works offline.
- **Rich Aesthetics**: Premium Dark Mode UI with Glassmorphic styling, Canvas-based custom spending charts, and budget limits.

---

## 🚀 Google Sheets Real-Time Sync Setup

To sync your expenses to a Google Sheet:

### 1. Create the Google Apps Script
1. Open a new or existing **Google Sheet**.
2. From the top menu, go to **Extensions > Apps Script**.
3. Delete any default code in the editor and paste the following template:

```javascript
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    
    // Appends [Date, Amount, Merchant, Category, Source]
    sheet.appendRow([
      new Date(data.timestamp),
      data.amount,
      data.merchant,
      data.category,
      data.sourceApp
    ]);
    
    return ContentService.createTextOutput(JSON.stringify({ status: "success" }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: error.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
```

### 2. Deploy the Script as a Web App
1. Click the **Deploy** button at the top-right of the script editor and select **New deployment**.
2. Click the gear icon next to "Select type" and choose **Web app**.
3. Configure the fields as follows:
   * **Description**: `GPay Expense Webhook`
   * **Execute as**: `Me (your-email@gmail.com)`
   * **Who has access**: `Anyone` *(Crucial: This allows the Android app to connect to the script without authentication issues)*.
4. Click **Deploy**.
5. Copy the **Web App URL** generated (it will look like `https://script.google.com/macros/s/.../exec`).

### 3. Configure the Android App
1. Open the app on your phone.
2. Tap the **Settings Gear Icon** in the top-right corner.
3. Paste the copied URL into the **Google Sheets Web App URL** text field.
4. Set your monthly budget limit and tap **Save**.

---

## 🛠️ Build and Run Locally

To build and deploy the app directly to your connected device:

1. Clone this repository to your computer.
2. Connect your Android device via USB and enable USB Debugging.
3. Open a terminal in the project root directory and run:
   ```bash
   ./gradlew installDebug
   ```
4. The application will compile and install on your phone automatically.
