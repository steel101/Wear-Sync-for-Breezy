# 🌤️ BreezeSync for Wear BreezeSync


A lightweight bridge application that seamlessly syncs detailed weather data from the [Breezy Weather Android app](https://github.com/breezy-weather/breezy-weather) to your Wear OS watch.

---

## 🔍 What It Breezy**Breezy Weather** is a fantastic open-source weather app, but it doesn't always have a native way to send its rich data to every Wear OS watch face or tile. This app acts as a secure, local **"sync bridge"**:

1. **Reads** weather data directly from Breezy Weather's internal database via a secure Content Provider.
2. **Processes** current conditions, detailed metrics (UV, Humidity, Wind, etc.), and forecasts.
3. **Pushes** that data to your connected Wear OS device instantly.

---

## ✨ Key Features

### ⚙️ Automation & Core
* **🔄 Automatic Background Sync:** Updates your watch every 45 minutes completely automatically.
* **⚡ Real-Time Updates:** Uses a `ContentObserver` to detect exactly when Breezy Weather refreshes, triggering an immediate watch sync.
* **🌐 Local & Private:** **Zero internet access.** It only reads data locally from your phone and sends it locally to your watch.
* **📐 Unit Support:** Automatically detects and respects your preferred system units (Celsius or Fahrenheit).

### 📊 Deep Weather Metrics

| 🌡️ Current Conditions | 📅 Forecasts |
| :--- | :--- |
| • Current Temp & "Feels Like"<br>• Wind Speed and Direction<br>• Air Quality Index (AQI) & Dew Point | • **7-Day Daily Forecast** (Highs/Lows/Icons)<br>• **6-Hour Hourly Forecast** |
| • Humidity, Pressure, UV Index, & Visibility<br>• Precipitation Probability (Rain Chance) | |

---

## 🚀 Installation & Setup

1. **Breezy Weather**
   Ensure you have the main app installed and configured on your phone.
   👉 [Get Breezy Weather on GitHub](https://github.com/breezy-weather/breezy-weather)
2. **Permissions**
   Open this sync app and tap **"Fetch & Sync"**. Grant the requested permission to read data from Breezy Weather.
3. **Watch App**
   Ensure the companion app component is installed on your Wear OS watch to receive and display the incoming data stream.
4. **Battery Optimization**
   For reliable background syncing, **disable "Battery Optimization"** for this app in your Android system settings so OS restrictions do not kill the background sync service.

---

## 🛠️ How It Works

* **The Phone App:** Displays a clean preview of the data being synced along with a **"Last Synced"** timestamp so you know your watch is up to date.
* **The Background Service:** Runs a `START_STICKY` background service that listens for system changes and handles communication via the **Google Play Services Wearable API**.

---

## 📋 Requirements

* 📱 Android Phone with **Breezy Weather** installed
* ⌚ **Wear OS Watch** actively connected via Bluetooth/Wi-Fi
* 🛠️ **Google Play Services** on both devices

---

## ⚠️ Disclaimer

> [!IMPORTANT]  
> This is an **independent companion app** and is not officially affiliated with, endorsed by, or maintained by the main Breezy Weather development team.
