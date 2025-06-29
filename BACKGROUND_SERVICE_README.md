# Background Service for PolarisClient App

## Overview
The PolarisClient app now includes a background service that can run continuously to collect cellular data and location information even when the app is not in the foreground.

## Features

### Background Service
- **Persistent Notification**: Shows a notification when the service is running
- **Data Collection**: Continuously collects cellular signal data and location information
- **Foreground Service**: Runs as a foreground service to prevent system termination
- **Easy Control**: Start/stop the service from the navigation drawer
- **Smart Permission Handling**: Automatically requests required permissions with explanations

### Notification Features
- Shows the number of data samples collected
- Provides a "Stop" action button
- Tapping the notification opens the main app
- Low priority notification (doesn't make sound/vibration)

### Data Collection Frequency
- **Location-based**: Collects data when location changes (every 30 seconds, 10 meters)
- **Timer-based**: Collects data every 15 seconds even when location doesn't change
- **Real-time Updates**: Notification updates with each data collection

## How to Use

### Starting the Background Service
1. Open the PolarisClient app
2. Open the navigation drawer (hamburger menu) from either:
   - MainActivity (any screen)
   - MapActivity (Cell Signal Map)
3. Go to "Settings" section
4. Tap "Start Background Service"
5. If permissions are missing, a dialog will explain what's needed
6. Grant the required permissions
7. The service will start automatically and show a persistent notification

### Stopping the Background Service
1. Either tap the "Stop" button in the notification, or
2. Go to the navigation drawer → Settings → "Stop Background Service" from any screen

### Theme Toggle
- Available from both MainActivity and MapActivity
- Navigation drawer → Settings → "Switch to Light/Dark Mode"
- Changes apply immediately across the entire app

### Map Features
- **Navigation Drawer**: Access all features from the Cell Signal Map
- **Refresh Button**: Toolbar refresh button to update map with latest data
- **Auto-refresh**: Map refreshes automatically when background service starts
- **Real-time Data**: View collected cellular data points on the map

### Service Status
- The menu item text changes based on service status:
  - "Start Background Service" when not running
  - "Stop Background Service" when running
- The notification shows the current status and data collection count

## Permission Requirements

### Required Permissions
- **Location Access** (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
  - Used to track your position for signal mapping
  - Required for background data collection
- **Phone State** (READ_PHONE_STATE)
  - Used to read cellular signal information
  - Required for signal strength data collection
- **SMS** (SEND_SMS)
  - Used for SMS testing functionality
  - Optional for background service

### Permission Handling
- App checks permissions on startup
- Clear explanations provided for each permission
- Automatic permission requests with user-friendly dialogs
- Background service starts automatically after permissions are granted
- Graceful handling of denied permissions

## Technical Details

### Service Components
- **BackgroundService**: Main service class that manages the foreground service
- **LocationService**: Handles location updates (30s intervals, 10m distance)
- **CellularService**: Collects cellular signal data
- **ServiceManager**: Provides a clean interface for service operations

### Data Collection Strategy
The service uses a dual approach for data collection:

1. **Location-triggered**: When location changes (every 30 seconds, 10 meters minimum)
2. **Timer-triggered**: Every 15 seconds regardless of location changes

This ensures continuous data collection even when the user is stationary.

### Data Storage
- All collected data is stored in the local database
- Includes location, signal strength, cell information, and timestamps
- Data can be viewed in the map interface

### Battery Optimization
- Location updates use reasonable intervals (30 seconds)
- Timer-based collection uses moderate frequency (15 seconds)
- Notification is set to low priority to minimize battery impact
- Service can be stopped by the user at any time

## Troubleshooting

### Service Won't Start
- Ensure all required permissions are granted
- Check if battery optimization is disabled for the app
- Restart the app if needed
- Check the permission dialog for missing permissions

### Service Stops Unexpectedly
- Check if the device has aggressive battery optimization
- Ensure the app is not being killed by the system
- The service is designed to restart automatically if killed

### Notification Issues
- Ensure notification permissions are enabled
- Check if the notification channel is created properly
- Restart the service if notification doesn't appear

### Permission Issues
- If permissions are denied, go to Settings → Apps → PolarisClient → Permissions
- Grant the required permissions manually
- Restart the app after granting permissions

## Development Notes

### Adding New Features
- Modify `BackgroundService.kt` to add new background tasks
- Update the notification to show relevant information
- Add new menu items in `drawer_menu.xml` if needed

### Service Lifecycle
- Service starts with `START_STICKY` to restart if killed
- Proper cleanup in `onDestroy()` method
- Location updates are properly removed when service stops
- Timer is properly cancelled on service destruction

### Testing
- Test on different Android versions
- Verify service survives app backgrounding
- Check battery usage impact
- Test notification interactions
- Test permission scenarios (granted/denied)
- Verify data collection frequency

### Data Collection Intervals
- Location updates: 30 seconds, 10 meters minimum distance
- Timer-based collection: 15 seconds
- These intervals can be adjusted in the code:
  - `LocationService.kt`: Change the interval values in `startListening()`
  - `BackgroundService.kt`: Change `DATA_COLLECTION_INTERVAL` constant 