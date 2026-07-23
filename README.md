# SmartWatch Sensor Collection

A native Wear OS application for collecting health measurements from a
Samsung Galaxy Watch4 for the Alera health-monitoring system.

## Current Features

- Continuous heart-rate collection
- Automatic on-demand SpO₂ measurement
- Configurable SpO₂ measurement interval
- Battery-level extraction
- Network connectivity detection
- JSON payload generation

## Technology

- Kotlin
- Jetpack Compose for Wear OS
- Samsung Health Sensor SDK
- Kotlin Serialization

## Tested Device

- Samsung Galaxy Watch4
- Model: SM-R860

## Required SDK

The Samsung Health Sensor SDK AAR is not included in this repository.

Place the SDK file inside:

app/libs/

## Current Status

Prototype sensor extraction is working. Watch-to-phone communication and
FastAPI integration are the next development phases.
