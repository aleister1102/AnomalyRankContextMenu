# Burp Extension: Anomaly Rank (Fork)

This extension allows you to select multiple requests in Burp Suite, calculate their anomaly rank, and view the results in a dedicated window.

**Note:** This is a fork of the original work by [Nick Coblentz](https://www.linkedin.com/in/ncoblentz/). We thank him for the initial foundation.

## Features

- **Context Menu Integration**: Right-click on requests and select "Apply Anomaly Rank".
- **Visual Analysis**:
  - Results displayed in a sortable, filterable table.
  - **Color-coded rows** for quick identification:
    - 🔴 **5xx**: Red
    - 🟠 **4xx**: Orange
    - 🟡 **3xx**: Yellow
    - 🟢 **2xx GET**: Green
    - 🔵 **2xx POST**: Blue
    - 🌸 **Other**: Pink
- **Split View**: Analyze Request and Response side-by-side.

## Usage

1. **Build the extension**:
   ```bash
   ./gradlew shadowJar
   ```
2. **Install in Burp Suite**:
   - Go to **Extensions** -> **Installed**.
   - Click **Add**.
   - Select the generated jar file from `build/libs/ApplyAnomalyRank-0.1.8-all.jar`.

3. **Run**:
   - Select requests in Proxy/Repeater/Logger.
   - Right-click -> **Extensions** -> **Apply Anomaly Rank**.
