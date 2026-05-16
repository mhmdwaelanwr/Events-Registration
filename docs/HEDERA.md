# Hedera Integration Reference

This document explains how the app submits a QR `registration_id` to Hedera Consensus Service (HCS). It is a concise reference for setup, flow, and troubleshooting.

## Overview

The app follows this workflow:

1. Scan QR code to extract `registration_id`.
2. Send `registration_id` to the API.
3. If API response is `success`, submit `registration_id` to Hedera Topic.

The Hedera submission is triggered in `AttendanceViewModel` after a successful API response.

## Where It Is Implemented

- Submission call: `app/src/main/java/anwar/mlsa/eventsregistration/viewmodel/AttendanceViewModel.kt`
- Hedera client logic: `app/src/main/java/anwar/mlsa/eventsregistration/Hedera.kt`

## Configuration

Hedera credentials are read from `local.properties` and exposed via `BuildConfig`.

Add these values to `local.properties` (do not commit them):

```
HEDERA_ACCOUNT_ID=0.0.xxxxx
HEDERA_PRIVATE_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
HEDERA_TOPIC_ID=0.0.xxxxx
```

## Runtime Flow

1. The QR scanner calls `markAttendance(registration_id)`.
2. API call `POST /attendance/mark` returns `success`.
3. `Hedera.submitRegistrationId(registration_id)` is executed on `Dispatchers.IO`.
4. The Hedera SDK signs the transaction and submits the message to the configured topic.

## Dependencies

Required dependencies (Android):

- Hedera SDK: `com.hedera.hashgraph:sdk:2.64.0`
- gRPC transport: `io.grpc:grpc-okhttp:1.59.0`

These are already added via `gradle/libs.versions.toml` and `app/build.gradle.kts`.

## Minimal Usage (Internal)

The app already calls Hedera automatically after the API succeeds. For a manual test, you can use the static entry in `Hedera.kt`:

```
Hedera.submitRegistrationId("REGISTRATION_ID_HERE")
```

## Common Errors

### No functional channel service provider found

Cause: gRPC transport not present on Android.

Fix: ensure `grpc-okhttp` is added to dependencies.

### DLSequence cannot be cast to ASN1OctetString

Cause: private key format mismatch (ED25519 vs ECDSA or invalid formatting).

Fix: make sure the key string is trimmed and in the correct Hedera format. The code attempts generic, ED25519, then ECDSA parsing.

## Notes

- Do not hardcode secrets in source code.
- `local.properties` is ignored by Git in this project.
- For stronger security, submit from a backend instead of the client.

