// Script to revert test mode changes back to normal flow
// Run this after testing the selfie video KYC functionality

// 1. Restore AppNavigation.kt from backup
// 2. Remove test mode indicators from SelfieKycScreen.kt

// Steps to revert:
// 1. Delete the current AppNavigation.kt
// 2. Rename AppNavigation.kt.backup to AppNavigation.kt
// 3. Remove the test mode changes from SelfieKycScreen.kt

// The changes made for testing:
// - Modified AppNavigation.kt to skip directly to SelfieKyc screen
// - Added test mode indicators in SelfieKycScreen.kt
// - Added "TEST MODE" text in headers and permission screens

// To revert manually:
// 1. In AppNavigation.kt, remove the LaunchedEffect that skips to SelfieKyc
// 2. In SelfieKycScreen.kt, remove the "TEST MODE" text and test indicators
// 3. Restore the original navigation flow 