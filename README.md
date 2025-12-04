# Power Plant Analyser

An Android application for monitoring, analyzing, and forecasting solar power plant performance with battery management capabilities.

## Features

### 📊 Analysis & Monitoring
- **Current Data**: Real-time monitoring of inverter metrics and system performance
- **Station Analysis**: Historical data analysis with recommendations
- **Battery Analysis**: Battery health, stress, and utilization analysis using machine learning
- **Generation Forecast**: AI-powered 7-day power generation forecasts with weather integration

### ⚙️ Configuration
- **Station Settings**: Configure inverter power, panel specifications, tilt angle, and location
- **Battery Settings**: Set up battery capacity, efficiency, and charge/discharge parameters
- **Performance Settings**: Adjust performance ratios and temperature coefficients
- **API Integration**: Connect to Solarman API for real-time data fetching

### 🌤️ Weather Integration
- Fetch weather forecasts from Open-Meteo API
- Display temperature, cloud cover, radiation, and wind speed
- Automatic weather data updates

### 📈 Data Visualization
- Interactive charts for power generation
- Daily and weekly energy statistics
- Battery state of charge tracking
- Historical data analysis

### 🔋 Battery Management
- Machine learning-based battery stress analysis
- Utilization tracking
- Charge/discharge optimization recommendations
- Battery health monitoring

## Installation

### Download APK
1. Go to [Releases](https://github.com/nekar5/Power-Plant-Analyser/releases)
2. Download the latest `Power Plant Analyser*.apk`
3. Enable "Install from unknown sources" on your Android device
4. Install the APK

### Requirements
- Android 9.0 (API 28) or higher
- Internet connection for weather data and API calls
- Camera (optional, for AR features)

## Getting Started

### Initial Setup

1. **Configure Station Settings**
   - Open the app and go to **Settings**
   - Enter your inverter power, panel specifications and location coordinates
   - Set panel efficiency and tilt angle

2. **Configure Battery Settings**
   - Set battery capacity, count, and type
   - Configure SOC (State of Charge) limits
   - Set roundtrip efficiency

3. **Connect to Solarman API**
   - Enter your Solarman API credentials
   - Configure device ID and serial number
   - Test the connection

4. **Load Data**
   - Go to **Analysis** tab, then **Station** and fetch **Weather** and **Station** data
   - Weather data is required first (location coordinates required)

### Using the App

#### Analysis Tab
- **Current**: View real-time inverter data and metrics
- **Station**: Analyze historical station data and get recommendations
- **Battery**: Monitor battery health and utilization
- **Forecast**: View 7-day power generation forecast

#### Project Tab
- Manage inverter, battery, and BMS configurations
- Create and edit project configurations
- AR design tools for visualizing solar panel layouts

#### Weather Tab
- View current weather conditions
- See 7-day weather forecast
- Monitor solar radiation and cloud cover

## Technology Stack

- **Language**: Java
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room Database (SQLite)
- **Machine Learning**: TensorFlow Lite (TensorFlow/Keras/scikit-learn models trained on PC)
- **Charts**: MPAndroidChart
- **API**: OkHttp (OpenMeteo API + Solarman API)
- **AR**: ARCore/Sceneform

## Machine Learning Features

The app uses TensorFlow Lite models for:
- **Power Generation Forecasting**: Predicts daily power output based on weather data
- **Battery Analysis**: Classifies battery usage patterns and predicts stress and usage levels

Models are included in the app assets and run locally on your device.

## Data Privacy

- All data is stored locally on your device
- Weather data is fetched from public APIs
- No personal data is transmitted to third parties
- API credentials are stored securely in local database

## Building from Source

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 36

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/nekar5/Power-Plant-Analyser.git
cd Power-Plant-Analyser
```

2. Open the project in Android Studio

3. Sync Gradle files

4. Build the project:
   - For debug: `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
   - For release: `Build` → `Generate Signed Bundle / APK`

### Configuration

Before building, you need to:
1. Create a keystore for signing release builds
2. Create `app/keystore.properties` with your keystore configuration
3. Configure your station settings in `app/src/main/res/raw/test_station_config.json`

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For issues, questions, or feature requests, please open an issue on GitHub.

## Version History

See [Releases](https://github.com/nekar5/Power-Plant-Analyser/releases) for the full changelog.

---

**Note**: This app requires proper configuration of station and battery settings for accurate analysis and forecasting. Make sure to enter correct specifications for your solar power plant setup.



