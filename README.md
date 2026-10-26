<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/452fd1e9-1044-48ad-9510-9ca03344e274

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

## Heart failure and low-flow research

From the ECG Grid Workbench, tap **Open heart-failure / low-flow research**. This opens a local, offline four-axis phenotype explorer integrated into the existing ECG app. The measurements and Python engine are exploratory and never automatically diagnose Stage C heart failure. See [the module documentation](research/heart_failure/README.md) for details, clinical limitations, tests and guideline references.

The GitHub Actions workflow runs the additional Python tests before building and testing the Android APK. Download the APK from the latest successful Actions artifact.

## Medical record research and uploads (v1.3)

From the Grid Workbench select **Open medical-record research & document upload**. Import local PDF, image, DOCX and text clinical records. OCR and a rule-based indexer extract candidate findings and preserve source excerpts for human review. Once the user explicitly consents, each opening of this screen refreshes every mapped medical-topic query against Europe PMC (including PubMed-indexed publications), displays publication metadata and links, and caches success/failures locally. Unrecognized clinical impressions are flagged for manual review; arbitrary records cannot be guaranteed to be fully extracted. No documents are sent to the literature server. These are research matches, not automated medical diagnoses, validated probabilities or treatment recommendations. See [design/medical-record-research.md](design/medical-record-research.md).

**v1.3.1 default launch:** The medical-record research workspace now opens on app launch. After consent, cached finding topics refresh automatically. The old ECG tracing workbench is reachable from an optional button.
