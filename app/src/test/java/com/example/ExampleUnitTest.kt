package com.example

import com.example.data.CardiacAlertEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCardiacAlertEngine_triggersHeartFailureOnLowEf() {
    val alerts = CardiacAlertEngine.analyzeMetrics(
      pWaveMs = 90,
      prMs = 160,
      qrsMs = 80,
      qtMs = 400,
      stElevationMv = 0.0f,
      tWaveMorphology = "Upright",
      efPercent = 30f, // Low Ejection Fraction
      lvIddMm = 45f,
      leftAtrialMm = 35f,
      ivsMm = 10f,
      pwMm = 10f,
      valveFunction = "Normal"
    )

    val hasHfAlert = alerts.any { it.title.contains("Heart Failure", true) || it.title.contains("Systolic Dysfunction", true) }
    assertTrue("Should trigger a Heart Failure alert for 30% EF", hasHfAlert)
  }

  @Test
  fun testCardiacAlertEngine_triggersSmallLvCavityAlert() {
    val alerts = CardiacAlertEngine.analyzeMetrics(
      pWaveMs = 90,
      prMs = 160,
      qrsMs = 80,
      qtMs = 400,
      stElevationMv = 0.0f,
      tWaveMorphology = "Upright",
      efPercent = 60f,
      lvIddMm = 32f, // Abnormally small Left Ventricular Internal Diameter End-Diastole (<35mm)
      leftAtrialMm = 35f,
      ivsMm = 10f,
      pwMm = 10f,
      valveFunction = "Normal"
    )

    val hasSmallCavityAlert = alerts.any { it.title.contains("Reduced LV Cavity Size", true) || it.title.contains("Small", true) }
    assertTrue("Should trigger Reduced LV Cavity Size alert for 32mm LV IDD", hasSmallCavityAlert)
  }

  @Test
  fun testCardiacAlertEngine_triggersStemiOnHighSt() {
    val alerts = CardiacAlertEngine.analyzeMetrics(
      pWaveMs = 90,
      prMs = 160,
      qrsMs = 80,
      qtMs = 400,
      stElevationMv = 2.5f, // ST segment elevation > 2mm
      tWaveMorphology = "Upright",
      efPercent = 60f,
      lvIddMm = 45f,
      leftAtrialMm = 35f,
      ivsMm = 10f,
      pwMm = 10f,
      valveFunction = "Normal"
    )

    val hasStemiAlert = alerts.any { it.title.contains("Myocardial Infarction", true) || it.title.contains("ST-Elevation", true) }
    assertTrue("Should trigger Acute STEMI alert for 2.5 mV ST elevation", hasStemiAlert)
  }
}
