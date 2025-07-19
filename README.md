# 🏢 Building Management App

A modern building/apartment management application that helps residents track their electricity and water consumption and pay bills conveniently.

## ✨ Key Features

### 🏠 Overview
- Real-time electricity and water consumption monitoring
- Current meter reading display
- Intuitive interface with Material 3 Design
- Push notifications for important updates

### 📊 Statistics & Charts
- Monthly/quarterly/yearly consumption reports
- Visual charts for usage trends
- Period-to-period consumption comparison

### 💳 Payment
- Online electricity and water bill payment via PayOS
- Secure payment gateway integration
- Detailed transaction history
- Payment reminders via notifications

### 👤 Account Management
- **Phone number authentication** with OTP verification
- Resident and room information
- Notification settings
- Personal profile management

## 🛠 Tech Stack

### Frontend
- **Kotlin** - Primary programming language
- **Jetpack Compose** - Modern UI toolkit
- **Material 3** - Google's latest design system
- **Navigation Compose** - Type-safe navigation

### Backend & Database
- **Firebase Authentication** - User authentication
- **Firebase Realtime Database** - Real-time database
- **Firebase Cloud Messaging (FCM)** - Push notifications
- **Firebase App Check** - App security

### Payment & Networking
- **PayOS** - Payment gateway integration
- **OkHttp** - HTTP client
- **HMAC SHA256** - Payment security
- **SSL/TLS** - Data encryption

## 📱 System Requirements

- **Android**: 7.0 (API level 24) or higher
- **Target SDK**: 35 (Android 15)
- **RAM**: Minimum 2GB
- **Storage**: 50MB free space

## 🚀 Installation

### Prerequisites
- Android Studio Arctic Fox or newer
- JDK 11
- Android SDK 35

### Step 1: Clone repository
```bash
git clone https://github.com/Shuzukochan/building-management-app.git
cd building-management-app
```

### Step 2: Configure Firebase
1. Create a new project on [Firebase Console](https://console.firebase.google.com/)
2. Add Android app with package name: `com.app.buildingmanagement`
3. Download `google-services.json` and place it in the `app/` directory
4. **Enable Authentication:**
   - Go to **Authentication** > **Sign-in method**
   - Enable **Phone** provider
   - Configure phone number verification settings
   - For testing, you can add test phone numbers in the Authentication settings

### Step 3: Configure API Keys
Create a `local.properties` file in the root directory with the following content:
```properties
# PayOS Configuration for payment processing
API_KEY=your_payos_api_key_here
CLIENT_ID=your_payos_client_id_here
SIGNATURE=your_payos_signature_here

# Firebase Configuration
FIREBASE_APPCHECK_DEBUG_TOKEN=your_debug_token_here
```

### Step 4: Build and run
```bash
./gradlew assembleDebug
```

### 📱 Authentication Setup Notes
- The app uses **Vietnamese phone number format (+84)**
- Users enter 10-digit phone numbers (starting with 0)
- App automatically converts to international format (+84)
- OTP verification is handled through Firebase Phone Authentication

## 🔐 Security

- **Firebase Phone Authentication**: Secure OTP-based login system
- **Firebase App Check**: Protects backend from invalid traffic
- **HMAC SHA256**: Payment data encryption
- **PayOS Integration**: Secure payment processing
- **ProGuard**: Code obfuscation in release builds

## 📸 Screenshots

### 🏠 Main Application Features

<div align="center">

<div style="display: flex; flex-wrap: wrap; justify-content: center; gap: 10px;">

<div style="text-align: center; width: 250px;">
<h4>Login</h4>
<img width="250" alt="Login Screen" src="https://github.com/user-attachments/assets/6ed6183e-e709-448b-9c12-fe4239e916df" />
</div>

<div style="text-align: center; width: 250px;">
<h4>OTP Verification</h4>
<img width="250" alt="OTP Verification" src="https://github.com/user-attachments/assets/de974d35-143a-46dd-8eba-47bfa7f72e55" />
</div>

<div style="text-align: center; width: 250px;">
<h4>Dashboard</h4>
<img width="250" alt="Home Dashboard" src="https://github.com/user-attachments/assets/910949da-3369-4158-8c1d-7d1d6002b61e" />
</div>

<div style="text-align: center; width: 250px;">
<h4>Statistics</h4>
<img width="250" alt="Chart Statistics" src="https://github.com/user-attachments/assets/ac60e07c-b8b9-4608-a745-fa6151aead51" />
</div>

<div style="text-align: center; width: 250px;">
<h4>Payment</h4>
<img width="250" alt="Payment Screen" src="https://github.com/user-attachments/assets/551fd0be-33fb-498e-9cc7-ea8aeff1254f" />
</div>

<div style="text-align: center; width: 250px;">
<h4>Settings</h4>
<img width="250" alt="Settings" src="https://github.com/user-attachments/assets/64616935-5ef5-4d0b-99cf-dd58ab0c94bd" />
</div>

</div>

</div>

### 💳 Payment Flow via PayOS

<div align="center">

<div style="display: flex; flex-wrap: wrap; justify-content: center; gap: 10px;">

<div style="text-align: center; width: 200px;">
<h4>Payment Setup</h4>
<img width="200" alt="Payment Setup" src="https://github.com/user-attachments/assets/7517c080-0914-4efa-99d1-80aa1a845e3a" />
</div>

<div style="text-align: center; width: 200px;">
<h4>QR Code Payment</h4>
<img width="200" alt="QR Code Scan" src="https://github.com/user-attachments/assets/f27c4ceb-c208-4db8-b041-505e7f1b63d2" />
</div>

<div style="text-align: center; width: 200px;">
<h4>Processing</h4>
<img width="200" alt="Payment Processing" src="https://github.com/user-attachments/assets/22acab18-4121-405d-9ceb-1c6077d7a19f" />
</div>

<div style="text-align: center; width: 200px;">
<h4>Payment Complete</h4>
<img width="200" alt="Payment Success" src="https://github.com/user-attachments/assets/6cf37c6e-9c33-430b-859a-45d960f738b2" />
</div>

</div>

</div>


## 🤝 Contributing

1. Fork this project
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is distributed under the MIT License. See `LICENSE` file for more information.

## 📞 Support

- **Email**: shuzukochan@gmail.com
- **Issues**: [GitHub Issues](https://github.com/Shuzukochan/building-management-web/issues)

## 🙏 Acknowledgments

Thank you for using Building Management System! If this project is helpful, please give us a ⭐ on GitHub.

- [Firebase](https://firebase.google.com/) - Backend as a Service
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI toolkit
- [Material Design 3](https://m3.material.io/) - Design system
- [PayOS](https://payos.vn/) - Payment gateway
- [OkHttp](https://square.github.io/okhttp/) - HTTP client

---

**Note**: This is a demo project. Please configure appropriate security measures before using in production environment. 
