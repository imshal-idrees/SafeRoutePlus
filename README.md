# SafeRoute+
A Community-Powered Mobile App for Safer and Smarter Pedestrian Navigation

## 1. Project Overview
SafeRoute+ is an Android application developed as a final year BSc Computer Science project. The aim of the application is to help users identify safer walking routes by combining:

- user-submitted safety reports
- a static local risk dataset
- map-based visualisation
- route comparison
- a basic AI text-classification component

The app allows users to:
- enter a start location and destination
- compare routes visually on a map
- submit reports about safety issues such as poor lighting or suspicious activity
- view static risk points from a local dataset
- see automatically classified reports as **safe** or **unsafe**
- view heatmap-based risk visualisation

This submission is a prototype for academic demonstration purposes.

---

## 2. Main Technologies Used
- **Kotlin**
- **Android Studio**
- **Google Maps SDK for Android**
- **Firebase Firestore**
- **Google Directions API**
- **Gson** for reading local JSON data

---

## 3. Project Folder Structure

### Root folder
- `README.md` – this file
- `build.gradle.kts` – project-level Gradle build file
- `settings.gradle.kts` – Gradle settings
- `gradle.properties` – Gradle configuration
- `local.properties` – local machine settings 

### Main app folder
- `app/src/main/java/com/imshal/saferouteplus/`
    - Kotlin source files such as:
        - `MainActivity.kt`
        - `DashboardActivity.kt`
        - `DirectionsService.kt`
        - `StaticRiskPoint.kt`
        - other model/service classes

- `app/src/main/res/`
    - layout XML files
    - drawable resources
    - values resources
    - manifest-linked assets

- `app/src/main/assets/`
    - local JSON datasets used by the application, including:
        - `crime_data.json`

- `app/google-services.json`
    - Firebase configuration file

---

## 4. Features Implemented

### 4.1 Route Planning
Users can enter a start location and destination. The system generates multiple route candidates and compares them.

### 4.2 Safety-Aware Routing
Routes are evaluated using:
- proximity to user-submitted reports
- proximity to static dataset risk points
- weighted risk values

The app highlights the safest and fastest routes separately.

### 4.3 User Reporting
Users can submit reports directly from the map, including:
- issue category
- free-text description
- report location

Reports are stored in Firebase Firestore.

### 4.4 Static Dataset Integration
The app loads a local JSON dataset containing static risk points such as:
- robbery hotspots
- poor lighting areas
- unsafe crossings
- anti-social behaviour locations

These points are displayed on the map and included in route risk scoring.

### 4.5 Basic AI Classification
A lightweight keyword-based text analysis component automatically classifies user-submitted reports as:
- `safe`
- `unsafe`

This classification is based on issue type and keywords in the user’s description.

### 4.6 Visualisation
The application includes:
- marker-based visualisation
- colour-coded risk markers
- heatmap display
- dashboard view

---

## 5. Marker Colour Scheme
The map uses the following marker colours:

- **Blue** – start and destination markers
- **Red** – unsafe user-submitted reports
- **Green** – safe user-submitted reports
- **Orange** – static dataset risk points

---

## 6. How to Build and Run the App

### Requirements
- Android Studio installed
- Android SDK installed
- Emulator or Android device
- Internet connection for map and directions features
- Valid Google Maps / Directions API key
- Firebase configuration (`google-services.json`)

### Steps
1. Open the project in Android Studio.
2. Allow Gradle to sync.
3. Ensure the required API key is configured as described in Section 7.
4. Run the app on an emulator or physical Android device.
5. For quick testing, an APK is also included in the submission package where applicable.

---

## 7. API Key Configuration
For security reasons, a live API key is not included in this submission package.

To run the map and directions features, the marker should create their own Google Maps Platform API key and insert it into the project before running the app.

### Step 1 – Create a Google Maps Platform API key
1. Go to the Google Cloud Console.
2. Sign in with a Google account.
3. Create a new project, or select an existing one.
4. In the Google Cloud Console, enable the APIs required by this app, such as:
    - Maps SDK for Android
    - Directions API
5. Go to **APIs & Services** → **Credentials**
6. Click **Create Credentials**
7. Select **API key**
8. Copy the generated API key

### Step 2 – Insert the API key into the project
1. Open the project in Android Studio
2. Press:
    - `Command + Shift + F` on Mac
    - `Ctrl + Shift + F` on Windows
3. Search for:
   `YOUR_API_KEY_HERE`
4. Replace every occurrence of `YOUR_API_KEY_HERE` with the generated API key
5. Save the files
6. Sync Gradle if prompted
7. Run the app

### Notes
- If the marker does not add an API key, the project source code can still be inspected, but map and directions functionality may not run correctly.

---

## 8. How to Test the Main Features

### Test 1 – Route generation
1. Launch the app
2. Leave the start field empty to use the default London start location, or enter a custom start location like 'Tower Bridge'
3. Enter a destination e.g. 'Oxford St'
4. Press the route button
5. Confirm that routes are displayed and differentiated
6. Confirm that:
    - the **blue route** represents the fastest route
    - the **green route** represents the safest route
7. Tap either the blue or green route to select it
8. Press **Start Navigation**
9. Confirm that Google Maps opens and begins walking navigation for the selected route

### Test 2 – Report submission
1. Tap the report button
2. Tap a point on the map
3. Select an issue type
4. Enter a description
5. Submit the report
6. Confirm the marker appears and is stored

### Test 3 – AI classification
1. Submit a report containing text such as:
    - “dark road”
    - “unsafe”
    - “well lit”
2. Confirm that the marker colour changes according to safe/unsafe classification:
    - **red** for unsafe
    - **green** for safe

### Test 4 – Static dataset
1. Launch the app
2. Confirm orange markers are shown for local risk points
3. Confirm these markers remain visible when routes are generated 
4. Confirm they contribute to route risk scoring

### Test 5 – Heatmap
1. Press the heatmap button
2. Confirm that the heatmap overlay appears or disappears correctly

---

## 9. Notes for Markers
- This is a prototype developed for academic assessment.
- Some data is user-generated and some is loaded from a local static dataset.
- The AI component is intentionally lightweight and rule-based.
- The app is designed to demonstrate safer route recommendation rather than provide production-ready navigation.

---

## 10. Author
**Imshal Idrees**  
BSc Computer Science  
City St George’s, University of London

---

## 11. Submission Contents
This product package includes:
- all source code required to run the Android app
- layout/resources/assets files
- local dataset files
- Firebase configuration
- this README