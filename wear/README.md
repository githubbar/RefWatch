# RefWatch: Wear OS Soccer Referee Assistant

![RefWatch App Icon/Screenshot (Optional)](./path/to/your/screenshot_or_icon.png) <!-- Optional: Add a nice screenshot or app icon -->

RefWatch is a dedicated Wear OS application designed to assist soccer referees in managing matches efficiently directly from their wrist. It provides essential tools for timekeeping, score tracking, and disciplinary actions, ensuring referees can focus on the game.

## Features

*   **Pre-Game Setup:**
    *   Designate **Home** or **Away** team for the initial kick-off.
    *   Select distinct **jersey colors** for each team for easy visual identification.
    *   Configure **half duration** (e.g., 45 minutes) and **halftime break duration** (e.g., 15 minutes).
*   **Match Timekeeping:**
    *   Accurate countdown timers for **first half, halftime, and second half**.
    *   **Play/Pause** functionality for the match timer, accessible via a long-press menu.
    *   (Optional: Add "Added Time" if implemented)
    *   Vibration alerts for period endings.
*   **Score & Event Logging:**
    *   **Main Game Screen:** Displays current score, period, and match timer.
    *   **Swipe-to-Action Screens:**
        *   Swipe Left (from main game screen): Access actions for the **Home Team** (log goal, log card).
        *   Swipe Right (from main game screen): Access actions for the **Away Team** (log goal, log card).
    *   Goals are automatically timestamped with the game clock time.
*   **Discipline Management:**
    *   Log **Yellow** and **Red cards**.
    *   Record the **player number** associated with each card.
    *   Cards are automatically timestamped with the game clock time.
*   **In-Game Menu (Long Press on Main Game Screen):**
    *   Access **Play/Pause** for the match timer.
    *   View **Game Log**: A chronological list of all significant events (goals, cards, period changes) with timestamps.
    *   **Reset Current Period Timer**: Resets the timer for the current active period to its full duration (e.g., if stopped incorrectly).
    *   **End Game & Reset / New Game**: Option to conclude the current match and prepare for a new one.
*   **User-Friendly Interface:**
    *   Designed for quick glances and easy interaction on small Wear OS screens.
    *   Clear visual indicators and intuitive navigation.

## Screenshots (Optional but Recommended)

<!-- Add 2-4 key screenshots here. You can use HTML for layout if needed. -->
<!-- Example:
<p float="left">
  <img src="./path/to/screenshot1.png" width="200" />
  <img src="./path/to/screenshot2.png" width="200" />
  <img src="./path/to/screenshot3.png" width="200" />
</p>
-->

## Getting Started

### Prerequisites

*   An Android device running Wear OS [Specify minimum Wear OS version, e.g., Wear OS 3.0 (API 30) or higher].
*   Android Studio [Specify version, e.g., Giraffe | 2022.3.1 or newer] to build and install.

### Building and Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/your-repo-name.git
    cd your-repo-name
    ```
2.  **Open in Android Studio:**
    Open the cloned project directory in Android Studio.
3.  **Build the project:**
    Let Gradle sync and then build the project (`Build > Make Project`).
4.  **Install on Emulator/Device:**
    *   Connect your Wear OS device or start a Wear OS emulator.
    *   Select the device/emulator from the deployment target dropdown in Android Studio.
    *   Click the "Run 'wear-app'" button (green play icon).

## Technologies Used

*   **Kotlin:** Primary programming language.
*   **Jetpack Compose for Wear OS:** Modern UI toolkit for building native Wear OS interfaces.
*   **Wear OS Material Components:** UI elements designed for Wear OS.
*   **StateFlow & ViewModel:** For reactive state management following MVVM architecture.
*   **HorizontalPager:** For swipeable action screens.
*   **SavedStateHandle:** For persisting game state across process death.
*   **CountDownTimer:** For match timekeeping.
*   **Vibrator API:** For haptic feedback.

## Project Structure (Brief Overview)

*   `data/`: Contains data models (`GameState`, `GameSettings`, `GameEvent`, Enums).
*   `navigation/`: Defines navigation routes (`Screen.kt`) and potentially the `NavHost` setup.
*   `presentation/`: UI-related code.
    *   `components/`: Reusable UI components (e.g., `ColorIndicator`, `PagerIndicator`, `ConfirmationDialog`).
    *   `dialogs/`: Dialog composables (e.g., `GameSettingsDialogWithPlayPause`).
    *   `screens/`: Composable functions for each screen of the app (e.g., `PreGameSetupScreen`, `SimplifiedGameScreen`, `HomeTeamActionScreen`).
    *   `theme/`: Theme definitions (`Color.kt`, `Theme.kt`, `Type.kt`).
*   `GameViewModel.kt`: The central ViewModel managing game logic and state.
*   `MainActivity.kt`: The main entry point activité for the Wear OS app, hosting the navigation.

## How to Use

1.  **Launch the App:** Open RefWatch on your Wear OS device.
2.  **Pre-Game Setup:**
    *   You'll be greeted by the "Match Setup" screen.
    *   Tap "Set Kick-off" to choose which team starts.
    *   Tap "Home Jersey" / "Away Jersey" to select team colors using the color picker.
    *   Adjust "Half Duration" and "Halftime Duration" using the steppers.
    *   Tap "Start Game."
3.  **Main Game Screen:**
    *   The timer will begin (or await your first Play action if not auto-started).
    *   View current score, period, and time.
    *   **Swipe left** to access Home team actions (Add Goal, Log Card).
    *   **Swipe right** to access Away team actions.
    *   **Long press** anywhere on the screen to open the in-game menu.
4.  **In-Game Menu (Long Press):**
    *   **Play/Pause Timer:** Control the match clock.
    *   **View Game Log:** See a history of events.
    *   **Reset Period Timer:** If you need to correct the current period's timer.
    *   **End Game & Reset / New Game:** Finalize the current match or start fresh.
5.  **Logging Events:**
    *   On the team-specific action screens, tap "Add Goal" or "Log Card."
    *   If logging a card, you will be prompted to enter the player number and select the card type.

## Future Enhancements (Roadmap)

*   [ ] Track added/stoppage time.
*   [ ] Option to log player names for cards/goals (complex input, maybe via voice or companion).
*   [ ] Substitution tracking.
*   [ ] Customizable vibration patterns/sound alerts.
*   [ ] Save and review past match summaries.
*   [ ] Settings screen for more persistent app preferences (e.g., default half duration).
*   [ ] Companion phone app for easier setup or data review.

## Contributing

Contributions are welcome! If you'd like to contribute, please follow these steps:

1.  Fork the repository.
2.  Create a new branch (`git checkout -b feature/your-feature-name`).
3.  Make your changes.
4.  Commit your changes (`git commit -m 'Add some feature'`).
5.  Push to the branch (`git push origin feature/your-feature-name`).
6.  Open a Pull Request.

Please make sure to update tests as appropriate.

## Known Issues / Limitations

*   [List any known issues, e.g., "Player number input for cards is currently basic numeric only."]
*   [e.g., "Added time is not yet implemented."]

## License

This project is licensed under the [Your Chosen License, e.g., MIT License or Apache 2.0] - see the [LICENSE.md](LICENSE.md) file for details.

---

**Fill in the Blanks:**

*   **`![RefWatch App Icon/Screenshot]`**: Replace `./path/to/your/screenshot_or_icon.png` with an actual path if you add an image.
*   **Prerequisites**: Adjust Wear OS version and Android Studio version if necessary.
*   **`git clone https://github.com/your-username/your-repo-name.git`**: Update with your actual GitHub username and repository name.
*   **Screenshots**: Add actual screenshots.
*   **Project Structure**: Fine-tune if your structure is slightly different.
*   **Future Enhancements**: Customize this list based on your actual plans.
*   **Known Issues**: Be honest about current limitations.
*   **License**: Choose a license (e.g., MIT, Apache 2.0) and add a `LICENSE.md` file to your repository. If you don't have one, you can easily find templates online.

**Tips for a Good README:**

*   **Concise and Clear:** Get straight to the point.
*   **Visuals:** Screenshots or a GIF can greatly enhance understanding.
*   **Easy to Follow Instructions:** Make it simple for someone else to build and run your project.
*   **Well-Structured:** Use Markdown headings, lists, and code blocks effectively.
*   **Up-to-Date:** Keep it current as your project evolves.

This template should give you a very solid starting point! Remember to replace placeholders and tailor it to the specifics of your RefWatch app.