# Bank Account Verification Feature

## Overview

The Bank Account Verification feature enables users to verify their bank account details before loan disbursal using a ₹1 UPI transfer (Reverse Penny Drop method). This provides a secure and user-friendly way to validate bank account ownership.

## Features

### 1. UPI Transfer Verification
- **Reverse Penny Drop**: User sends ₹1 from their bank account to the app's UPI handle
- **Auto-detection**: System automatically detects the transfer and fetches account details
- **Secure**: Uses UPI transaction reference for secure verification

### 2. Account Details Display
- **Auto-fetched Details**: 
  - Account Holder Name
  - Account Number (masked for security)
  - IFSC Code
  - Bank Name
- **Visual Review**: Clean card-based UI showing verified details

### 3. User Confirmation
- **Checkbox Confirmation**: User must confirm account details belong to them
- **Proceed Button**: Only enabled after confirmation
- **Analytics Tracking**: All user interactions are tracked

## Technical Implementation

### Data Models
- `BankAccountVerificationRequest/Response`: API request/response models
- `BankAccountDetailsData`: Account details structure
- `BankAccountVerificationState`: UI state management

### API Endpoints
- `POST /api/bank-account/verification/initiate`: Start verification process
- `POST /api/bank-account/verification/details`: Fetch account details
- `POST /api/bank-account/verification/confirm`: Confirm account details

### UI Components
- `BankAccountVerificationScreen`: Main UI screen
- `BankAccountVerificationViewModel`: Business logic
- `BankAccountRepository`: Data layer

## User Flow

1. **Initial State**: User sees instructions and "Start Verification" button
2. **UPI Ready**: Displays UPI handle with copy/share functionality
3. **Transfer Detection**: Shows loading while waiting for ₹1 transfer
4. **Account Details**: Displays fetched account details in review card
5. **Confirmation**: User confirms details and proceeds
6. **Success**: Verification complete, navigates to next screen

## Error Handling

### Timeout
- 2-minute timeout for transfer detection
- Clear error message with retry option
- User guidance on correct UPI account usage

### Network Errors
- Comprehensive error handling for API failures
- Retry mechanism for failed requests
- User-friendly error messages

### UPI App Issues
- Fallback mechanism for UPI app detection
- Support for multiple UPI apps (Google Pay, Paytm, PhonePe, etc.)
- Generic UPI intent as final fallback

## Security Features

- **Account Number Masking**: Only last 4 digits displayed
- **Secure API Calls**: All requests use proper authentication
- **Device ID Tracking**: Unique device identification
- **Transaction Reference**: Secure verification using UPI transaction ID

## Analytics Integration

The feature includes comprehensive analytics tracking:
- Button clicks and user interactions
- Feature usage tracking
- API call success/failure logging
- Conversion tracking for verification completion

## Navigation Integration

The bank account verification screen is integrated into the main app flow:
- Triggered after KYC completion (Aadhaar or Selfie)
- Navigates to success screen upon completion
- Maintains app navigation consistency

## Testing

Basic unit tests are included for:
- ViewModel state management
- User interaction handling
- Error state validation

## Future Enhancements

1. **Multiple Bank Support**: Allow users to verify multiple accounts
2. **Enhanced UPI Integration**: Direct integration with UPI apps
3. **Offline Support**: Cache verification data for offline access
4. **Advanced Security**: Additional verification layers
5. **International Support**: Extend to other payment systems

## Usage Example

```kotlin
// Initialize ViewModel
val viewModelFactory = BankAccountVerificationViewModelFactory(context)
val viewModel = viewModel<BankAccountVerificationViewModel>(factory = viewModelFactory)

// Use in Composable
BankAccountVerificationScreen(
    viewModel = viewModel,
    onVerificationComplete = {
        // Navigate to next screen
        onNavigate(Screen.Success)
    },
    context = context
)
```

## Configuration

The feature can be configured through:
- API endpoints in `BankAccountApi`
- Timeout settings in `BankAccountRepository`
- UPI app preferences in repository
- UI customization in screen components

This implementation provides a robust, user-friendly bank account verification system that integrates seamlessly with the existing loan application flow. 