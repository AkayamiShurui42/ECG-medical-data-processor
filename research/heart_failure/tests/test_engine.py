import unittest
from research.heart_failure.hf_subtype_engine import assess_heart_failure_phenotype, classify_heart_failure

class TestPhenotype(unittest.TestCase):
    def test_no_implicit_patient_profile(self):
        r = assess_heart_failure_phenotype({})
        self.assertIsNone(r["four_axis_findings"]["flow"]["low_svi_research_flag"])
        self.assertIsNone(r["clinical_diagnosis"])
        self.assertFalse(r["stage_c_confirmed"])

    def test_low_flow_is_a_flag_not_diagnosis(self):
        r = assess_heart_failure_phenotype({
            "svi": 25, "hr": 101, "lvedvi": 45, "lvmi": 46, "lvef": 55,
            "symptoms_documented": True, "cardiac_abnormality_documented": True
        })
        f = r["four_axis_findings"]
        self.assertTrue(f["flow"]["low_svi_research_flag"])
        self.assertEqual(f["flow"]["derived_cardiac_index_l_min_m2"], 2.525)
        self.assertEqual(f["geometry"]["estimated_mcf_percent"], 57.07)
        self.assertIsNone(r["clinical_diagnosis"])

    def test_normal_ef_does_not_infer_flow(self):
        r = assess_heart_failure_phenotype({"lvef": 55})
        self.assertIsNone(r["four_axis_findings"]["flow"]["low_svi_research_flag"])

    def test_measurement_consistency(self):
        r = assess_heart_failure_phenotype({"svi": 20, "lvedvi": 80, "lvef": 50})
        self.assertTrue(r["four_axis_findings"]["geometry"]["volume_svi_discordance_gt_25pct"])

    def test_legacy_input(self):
        self.assertTrue(classify_heart_failure({"new_svi": 25})["four_axis_findings"]["flow"]["low_svi_research_flag"])

    def test_reject_invalid(self):
        for data in ({"svi": -1}, {"lvef": 101}, {"hr": float("nan")}):
            with self.subTest(data=data), self.assertRaises(ValueError):
                assess_heart_failure_phenotype(data)

if __name__ == "__main__":
    unittest.main()
