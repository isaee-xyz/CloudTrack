# CloudTrack 📱☁️

**CloudTrack** is a professional-grade call tracking and synchronization tool that seamlessly links Android phone activity (PSTN & WhatsApp) with **LeadSquared CRM**.

## ✨ Features
- **Smart PSTN Tracking**: Records calls, durations, and caller IDs automatically.
- **WhatsApp Support**: Syncs WhatsApp and WhatsApp Business calls via notification listening.
- **Cloud Vault**: High-quality audio recordings are stored securely in the cloud.
- **App Owner Profile**: Manual phone number verification to bypass SIM detection unreliability.
- **Lead-Owner Sync**: Advanced verification ensures activities are only posted for authorized lead owners in LeadSquared.
- **Privacy First**: Automated cleanup policy deletes personal calls (non-leads) instantly.
- **SIM Tracking**: Support for dual-SIM devices with automatic detection of the dialed number.
- **Real-time History**: Stream call recordings and metadata directly from the cloud.

## 🚀 Getting Started

### Prerequisites
- Firebase Project with Firestore and Storage enabled.
- LeadSquared API Access (Access Key, Secret Key).
- Android Device (API 26+).

### Cloud Setup
1. Clone the repository.
2. Initialize Firebase: `firebase init`
3. Deploy the Cloud Functions: `firebase deploy`

### Android Setup
1. Add your `google-services.json` to the `app/` folder.
2. Build and run via Android Studio.
3. Grant permissions for:
   - Call Logs (Metadata)
   - Accessibility (Audio Capture)
   - Notifications (WhatsApp Tracking)

## 🛡️ Security & Privacy
- **Zero-Key Leak**: All API secrets are stored as environment variables in Firebase Cloud.
- **Encryption**: Data is encrypted in transit using SSL.
- **Privacy Shield**: The system only retains calls that match existing prospects in your CRM.

## 🏗️ Architecture
For a deep dive into how CloudTrack works, see [ARCHITECTURE.md](ARCHITECTURE.md).

---
*Created with ❤️ for high-performance sales teams.*
