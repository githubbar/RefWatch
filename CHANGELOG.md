# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.7] - 2025-05-15

### Added
- **ICS Import Support**: Added capability to import referee match assignments from `.ics` calendar files.
- **Match Analytics (Mobile)**: Introduced a new Match Analytics screen with soccer field heatmap visualization for tracking referee movement.
- **Position Tracking Toggle**: Added a setting to enable/disable collection of position information during matches.
- **Authentication**: Implemented `AuthRepository` on mobile for secure user sign-in and data syncing.

### Changed
- **Kick-off Selection**: Improved UI for selecting initial kick-off and team colors.
- **UI Refinements**: Updated theme colors and polished various screens on both Wear OS and Mobile.
- **Game Settings**: Enhanced the in-game settings screen with more control over the match state.

### Fixed
- Improved overall stability of data sync between Wear OS and Mobile.
